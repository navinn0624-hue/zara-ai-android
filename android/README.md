# MY ASSISTANT (Hey NOVA) 🎙️⚡

An advanced on-device personal AI voice assistant for Android smartphones, combining the conversational elegance of Siri with autonomous AI agent capabilities powered by Google Gemini.

Designed with a futuristic dark-neon aesthetic, real-time audio waveform visualizers, native Tamil/English code-switching intelligence, and a secure human-in-the-loop action execution framework.

---

## 🌟 Key Features

### 1. Dual-Language & Tanglish Intelligence (தமிழ் + English)
- **Automatic Language Detection**: Detects Tamil script (`\u0B80..\u0BFF`), colloquial Tanglish phonetic phrasing (*"alarm vai 7 am ku"*, *"whatsapp open pannu"*, *"naalaiku weather eppadi irukku"*), and standard English.
- **Dynamic TTS Locale Switching**: Automatically routes output to the Android Tamil TTS engine (`ta-IN`) or English (`en-US`) with custom pitch and cadence control.

### 2. On-Device Wake Word ("Hey NOVA")
- **Wake Word Architecture**: Integrated keyword spotter pipeline evaluating on-device keyword engines (Picovoice Porcupine, Vosk offline, and Android VoiceInteractionService).
- **Privacy Compliance**: Strictly adheres to Android 12+ microphone indicators and background restrictions.
- **Manual Trigger Fallback**: Tap-to-activate glowing AI Orb, one-touch microphone button, and Assist intent support.

### 3. AI Agent & Device Control Engine
Transforms unstructured speech into structured, type-safe device actions:
- 📱 **Application Launcher**: Detects and opens any installed Android app by name or package.
- ⏰ **Alarms & Timers**: Sets exact system alarms and countdown timers via Android's `AlarmClock` API.
- ⚙️ **Settings Quick-Open**: Direct access to Wi-Fi, Bluetooth, Display, Sound, and Battery settings.
- 📞 **Phone Calls (Safe Gate)**: Initiates phone calls with mandatory interactive user confirmation.
- 💬 **SMS & Messaging (Safe Gate)**: Drafts outgoing messages for explicit user verification before dispatch.
- 🧠 **Long-Term Memory**: Stores and retrieves persistent facts, preferences, and personal details via Room DB.

### 4. Futuristic Dark UI/UX
- **Animated AI Orb**: Custom Compose `Canvas` with multi-stop radial gradient glow, sinusoidal breathing rhythms, and reactive scale pulsation driven by live mic decibels.
- **Voice Waveform**: 18-bar real-time audio visualizer displaying live input decibel energy.
- **Glassmorphism Chat History**: Full message timeline with language badges, timestamps, and action execution tags.
- **Interactive Action Cards**: Authorize or decline sensitive phone actions with one tap.

---

## 🛡️ Security & Privacy Architecture

- **Zero Hardcoded Secrets**: No API keys are stored in the APK or committed to Git.
- **Keystore Encryption**: User-entered Gemini API keys are encrypted at rest using AES-256-GCM via `EncryptedSharedPreferences` backed by the Android Hardware Keystore.
- **Human-in-the-Loop Gate**: All sensitive actions (calls, messages, purchases) require explicit user approval.
- **Auditable Action Logs**: All dispatched intents and actions are logged to a local Room database audit trail.

---

## 🏗️ Technical Stack

- **Language**: Kotlin 1.9.23 / 2.0 (100% Coroutines & Flow)
- **UI Framework**: Jetpack Compose (Material 3 BOM 2024.04.01)
- **Architecture**: Clean Architecture + MVVM (Unidirectional Data Flow)
- **Local Database**: Room 2.6.1 with KSP
- **Networking & AI**: OkHttp 4.12.0 + Google Gemini REST API (Gemini 1.5 Flash)
- **Audio**: `android.speech.SpeechRecognizer` + `android.speech.tts.TextToSpeech`
- **Security**: AndroidX Security Crypto (`androidx.security:security-crypto`)

---

## 🚀 Setup & Build Instructions

### Prerequisites
1. **Android Studio** (Hedgehog 2023.1.1 or newer / Ladybug)
2. **Android SDK** API Level 34 (compileSdk 34, minSdk 26)
3. **JDK 17** or newer (bundled with Android Studio JBR)
4. A **Google Gemini API Key** (obtain free from [Google AI Studio](https://aistudio.google.com/))

### 1. Clone & Configure
```bash
git clone <your-repo-url>
cd "2nd AI"
```

### 2. Configure Local Properties
Copy `local.properties.example` to `local.properties`:
```properties
sdk.dir=C\:\\Users\\<Username>\\AppData\\Local\\Android\\Sdk
```

### 3. Build APK
From Android Studio:
- Open project folder.
- Allow Gradle sync to complete.
- Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Or via command line:
```bash
./gradlew assembleDebug
```
The resulting debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Running Unit Tests
```bash
./gradlew test
```

### 5. Runtime Configuration
1. Launch **NOVA Assistant** on your device or emulator.
2. Grant the **Microphone** permission when prompted.
3. Tap the **Settings** gear icon in the top-right corner.
4. Enter your **Gemini API Key** and tap **Save Key**.
5. Tap the central glowing **AI Orb** or say *"Hey NOVA"* to start interacting!

---

## 📋 Implementation Status & Roadmap

| Feature | Phase | Status | Notes |
|---|---|---|---|
| Project Architecture & Scaffold | Phase 1 | Completed | MVVM + Clean Architecture layout |
| Futuristic Dark Theme & UI | Phase 1 | Completed | OLED Black, Neon Cyan & Violet |
| Animated Canvas AI Orb | Phase 1 | Completed | Multi-stop radial gradients & state reactivity |
| Real-Time Voice Waveform | Phase 1 | Completed | Microphone RMS decibel visualization |
| Speech Recognition Engine | Phase 1 | Completed | Tamil + English + Tanglish recognition |
| Text-to-Speech Engine | Phase 1 | Completed | Dynamic script/locale switching (`ta-IN`/`en-US`) |
| Room Persistence | Phase 1 | Completed | Conversation, Memory, and Action tables |
| Gemini API Client | Phase 1 | Completed | Function calling, system instructions, safety |
| Encrypted Preferences | Phase 1 | Completed | AES-256 Keystore-backed key storage |
| Device Action Dispatcher | Phase 2 | Completed | Alarms, timers, apps, settings intents |
| Sensitive Action Approval Card | Phase 2 | Completed | Human-in-the-loop confirmation flow |
| On-Device Wake Word | Phase 3 | Architectural Scaffold | Interface & fallback listener ready; Porcupine/Vosk integration documented |
| Offline Keyword Model (JNI) | Phase 3 | Planned | Offline .onnx / .ppn binary weights packaging |
| Background Service Notification | Phase 3 | Planned | Foreground mic service with ongoing notification |

---

## 📄 License
This project is open-source under the MIT License.
