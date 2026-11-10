package com.example.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.sin

/**
 * Gerenciador nativo de ferramentas críticas e hardware de baixo consumo:
 * 1. Lanterna SOS em Código Morse internacional (... --- ...) via CameraManager.
 * 2. Apito Acústico de Emergência gerando onda senoidal pura a 3000 Hz via AudioTrack.
 * 3. GPS Pulso "On-Demand": ativação sob demanda com timeout estrito de 8s e desligamento imediato.
 */
class HardwareToolsManager(private val context: Context) {

    companion object {
        private const val TAG = "HardwareToolsManager"
        const val WHISTLE_FREQUENCY_HZ = 3000 // Frequência ótima de penetração acústica humana
        const val SAMPLE_RATE = 44100
        const val GPS_TIMEOUT_MS = 8000L

        // Padrão Morse Internacional: ponto = 200ms, traço = 600ms
        const val MORSE_DOT_MS = 200L
        const val MORSE_DASH_MS = 600L
        const val MORSE_ELEMENT_GAP_MS = 200L
        const val MORSE_LETTER_GAP_MS = 600L
        const val MORSE_CYCLE_GAP_MS = 1400L
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    // Estados observáveis para a UI
    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isWhistleActive = MutableStateFlow(false)
    val isWhistleActive: StateFlow<Boolean> = _isWhistleActive.asStateFlow()

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    // Rastreio contínuo de movimentação em tempo real (Live Tracking)
    private val _isLiveTracking = MutableStateFlow(false)
    val isLiveTracking: StateFlow<Boolean> = _isLiveTracking.asStateFlow()

    private val _liveLocation = MutableStateFlow<Location?>(null)
    val liveLocation: StateFlow<Location?> = _liveLocation.asStateFlow()

    private var liveLocationListener: LocationListener? = null

    private var sosJob: Job? = null
    private var whistleJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var cameraIdWithFlash: String? = null

    init {
        findCameraWithFlash()
    }

    private fun findCameraWithFlash() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraIdWithFlash = id
                    break
                }
            }
            if (cameraIdWithFlash == null && cameraManager.cameraIdList.isNotEmpty()) {
                cameraIdWithFlash = cameraManager.cameraIdList[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao identificar câmera com flash", e)
        }
    }

    // =========================================================================
    // 1. LANTERNA SOS EM CÓDIGO MORSE (... --- ...)
    // =========================================================================

    /**
     * Alterna a lanterna comum contínua ON / OFF
     */
    fun toggleTorch(): Boolean {
        val cid = cameraIdWithFlash ?: return false
        val newState = !_isTorchOn.value
        return try {
            if (_isSosActive.value) stopSosMorse()
            cameraManager.setTorchMode(cid, newState)
            _isTorchOn.value = newState
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alternar modo lanterna", e)
            false
        }
    }

    /**
     * Inicia o SOS em Código Morse internacional (... --- ...) em loop.
     * Funciona em background contínuo mesmo com tela desligada.
     */
    fun startSosMorse() {
        val cid = cameraIdWithFlash ?: return
        if (_isSosActive.value) return

        _isSosActive.value = true
        _isTorchOn.value = false

        sosJob?.cancel()
        sosJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && _isSosActive.value) {
                    // S: 3 curtos (...)
                    repeat(3) {
                        setTorchState(cid, true)
                        delay(MORSE_DOT_MS)
                        setTorchState(cid, false)
                        delay(MORSE_ELEMENT_GAP_MS)
                    }

                    delay(MORSE_LETTER_GAP_MS - MORSE_ELEMENT_GAP_MS)

                    // O: 3 longos (---)
                    repeat(3) {
                        setTorchState(cid, true)
                        delay(MORSE_DASH_MS)
                        setTorchState(cid, false)
                        delay(MORSE_ELEMENT_GAP_MS)
                    }

                    delay(MORSE_LETTER_GAP_MS - MORSE_ELEMENT_GAP_MS)

                    // S: 3 curtos (...)
                    repeat(3) {
                        setTorchState(cid, true)
                        delay(MORSE_DOT_MS)
                        setTorchState(cid, false)
                        delay(MORSE_ELEMENT_GAP_MS)
                    }

                    // Pausa entre ciclos completos de SOS
                    delay(MORSE_CYCLE_GAP_MS)
                }
            } finally {
                setTorchState(cid, false)
                _isSosActive.value = false
            }
        }
    }

    fun stopSosMorse() {
        _isSosActive.value = false
        sosJob?.cancel()
        sosJob = null
        cameraIdWithFlash?.let { setTorchState(it, false) }
    }

    private fun setTorchState(cameraId: String, enabled: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao definir estado da lanterna", e)
        }
    }

    // =========================================================================
    // 2. APITO ACÚSTICO DE EMERGÊNCIA (3000 Hz PURO VIA AUDIOTRACK)
    // =========================================================================

    /**
     * Inicia o apito de resgate a 3000 Hz gerado matematicamente com onda senoidal.
     * Frequência ideal para penetração através de concreto e escombros.
     */
    fun startWhistle(isPulseMode: Boolean = true) {
        if (_isWhistleActive.value) return
        _isWhistleActive.value = true

        whistleJob?.cancel()
        whistleJob = scope.launch(Dispatchers.Default) {
            val numSamples = SAMPLE_RATE // 1 segundo de áudio gerado
            val generatedSnd = ShortArray(numSamples)
            val doubleFreq = WHISTLE_FREQUENCY_HZ.toDouble()

            // Síntese de onda senoidal pura a 3000 Hz
            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / doubleFreq)
                generatedSnd[i] = (sin(angle) * Short.MAX_VALUE * 0.95).toInt().toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, numSamples * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            try {
                while (isActive && _isWhistleActive.value) {
                    // Escreve os dados senoidais no buffer do AudioTrack
                    track.write(generatedSnd, 0, generatedSnd.size)

                    if (isPulseMode) {
                        // Modo pulso: 700ms som, 300ms silêncio tático
                        delay(700)
                        track.pause()
                        delay(300)
                        if (isActive && _isWhistleActive.value) {
                            track.play()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no loop de reprodução do apito", e)
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
                audioTrack = null
                _isWhistleActive.value = false
            }
        }
    }

    fun stopWhistle() {
        _isWhistleActive.value = false
        whistleJob?.cancel()
        whistleJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar AudioTrack", e)
        }
        audioTrack = null
    }

    // =========================================================================
    // 3. GPS PULSO ("ON-DEMAND" COM TIMEOUT DE 8s)
    // =========================================================================

    data class GpsPulseResult(
        val location: Location?,
        val providerUsed: String,
        val durationMs: Long,
        val isSuccess: Boolean
    )

    /**
     * Ativa o GPS do aparelho sob demanda, aguarda 1 única coordenada precisa
     * ou cancela em até 8 segundos, desligando o rádio GPS de imediato.
     */
    @SuppressLint("MissingPermission")
    suspend fun requestSingleShotLocation(): GpsPulseResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()

        // Verificação de permissões locais
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@withContext GpsPulseResult(null, "NO_PERMISSION", 0, false)
        }

        _isGpsActive.value = true

        val result = withTimeoutOrNull(GPS_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) {
                            cont.resume(loc)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                // Tenta GPS_PROVIDER se habilitado, com fallback para NETWORK_PROVIDER
                val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val hasNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (hasGps && hasFine) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                } else if (hasNet) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                } else {
                    val lastKnown = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    cont.resume(lastKnown)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        _isGpsActive.value = false

        if (result != null) {
            GpsPulseResult(result, result.provider ?: "GPS", duration, true)
        } else {
            // Em caso de timeout no fix de satélites dentro dos 8s, busca o último cache passivo
            val lastCached = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            } catch (e: Exception) {
                null
            }
            GpsPulseResult(lastCached, "CACHE_FALLBACK", duration, lastCached != null)
        }
    }

    // =========================================================================
    // 4. RASTREIO CONTÍNUO DE MOVIMENTAÇÃO (LIVE TRACKING NO MAPA)
    // =========================================================================

    @SuppressLint("MissingPermission")
    fun startLiveLocationTracking(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return false
        if (_isLiveTracking.value) return true

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                _liveLocation.value = loc
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        return try {
            liveLocationListener = listener
            _isLiveTracking.value = true
            _isGpsActive.value = true

            // Emite imediatamente a última posição conhecida se disponível
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                _liveLocation.value = lastKnown
            }

            if (hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L, // Atualiza a cada 1.5s
                    1.0f,  // Ou a cada 1 metro de deslocamento
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2500L,
                    1.5f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar rastreio de movimentação ao vivo", e)
            _isLiveTracking.value = false
            _isGpsActive.value = false
            false
        }
    }

    fun stopLiveLocationTracking() {
        try {
            liveLocationListener?.let { locationManager.removeUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar rastreio de movimentação", e)
        }
        liveLocationListener = null
        _isLiveTracking.value = false
        _isGpsActive.value = false
    }

    /**
     * Limpeza geral de hardware ao encerrar
     */
    fun releaseAll() {
        stopSosMorse()
        stopWhistle()
        stopLiveLocationTracking()
        _isTorchOn.value = false
        _isGpsActive.value = false
    }
}
