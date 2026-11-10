# ResilienceHub Android 🛡️📱

**ResilienceHub Android** is an advanced, offline-first civil preparedness and family emergency management application built with native Kotlin and Jetpack Compose. Designed for urban apartment resilience, crisis response, and zero-connectivity scenarios.

## 🚀 Key Features & Architecture

* **FEFO Inventory & Rationing Engine:** Automated stock management (apartment static reserve vs. 72h bug-out bag) for water, food, and medical supplies. Features smart consumption rotation alerts and dynamic autonomy calculation based on family size (2.5L/person/day for apartment shelter-in-place, 2.0L/person/day for bug-out bag).
* **Native Offline Barcode Scanning:** Real-time barcode & QR Code scanner powered by CameraX and ML Kit Offline (EAN-13, EAN-8, QR Code, Code 128) with low-latency on-device optical recognition.
* **Offline Vector Maps & On-Demand GPS:** Integrated with **MapLibre Native SDK** for offline vector maps. Features a strict "GPS Pulse" mechanism that wakes the hardware location manager for a single shot (max 8s timeout) to calculate Haversine distances and cardinal bearings to safe family meeting points, preserving maximum battery life.
* **Local AI RAG (Retrieval-Augmented Generation):** Built with **MediaPipe Tasks GenAI (Gemma 2B)** and SQLite FTS4/FTS5 via Room. Imports official civil defense, fire department, and first-aid PDFs, chunking and indexing text locally for instant offline search and precise AI-assisted emergency guidance (zero hallucinations, deterministic strict prompt groundings, temperature = 0.1f, topK = 1).
* **Crisis Mode OLED UI:** Fully reactive Jetpack Compose Material 3 interface featuring a pure black `#000000` OLED crisis theme to eliminate power consumption on AMOLED displays during blackouts.
* **Native Hardware Utilities:** 
  * SOS Morse Code flashlight controller via `CameraManager` (`... --- ...`).
  * High-frequency acoustic siren (3000 Hz pure sine wave generator via `AudioTrack`) for search and rescue signaling.
  * Direct emergency dialer integration (199 Civil Defense, 193 Firefighters, 192 SAMU, 190 Police, 0800 Intoxication ANVISA).
  * Offline Public AM/FM radio frequency directory (Rio de Janeiro).
* **Encrypted Family Vault & Pediatric Calculator:** Emergency medical profiles (blood type, allergies, continuous medication) and fast weight-based dosage reference.
* **Background Automation:** Daily validity checks and battery/power bank maintenance reminders (every 90 days) handled via Android `WorkManager` (100% offline).

## 🛠️ Tech Stack

* **Language:** 100% Kotlin
* **UI:** Jetpack Compose, Material 3
* **Architecture:** MVVM, Clean Architecture, Coroutines & Flow
* **Database:** Room Database, SQLite FTS4/FTS5 Full-Text Search
* **Maps:** MapLibre Native Android SDK (`org.maplibre.gl:android-sdk`)
* **Camera & Barcode:** CameraX (`camera-camera2`, `camera-view`, `camera-lifecycle`) + Google ML Kit Barcode Scanning Offline
* **On-Device AI:** Google AI Edge / MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai`)
* **Document Extraction:** PDFBox-Android (`com.tom-roush:pdfbox-android`)
* **Background Processing:** Android WorkManager (`androidx.work:work-runtime-ktx`)

## 📦 How to Build

1. Clone the repository:
   ```bash
   git clone https://github.com/Magnostico/ResilienceHub.git
   ```
2. Open the project in **Android Studio** (Koala / Ladybug or newer with JDK 17+).
3. Sync Gradle and build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
