# 🚀 ShareProxy

An advanced Android network utility designed to manage, inject, and route proxy protocols seamlessly via a high-performance TUN interface, featuring automated configuration parsing from the system clipboard.

![Android Target](https://img.shields.io/badge/Android-SDK%2034-brightgreen?style=flat-square&logo=android)
![Protocol](https://img.shields.io/badge/Protocol-HTTPS%20%2F%20TUN-blue?style=flat-square)
![Build](https://img.shields.io/badge/AGP-8.0.0-orange?style=flat-square)

---

## ✨ Key Features

- **🌐 Core TUN Routing Engine**
  Intercepts device network traffic utilizing a dedicated Android `VpnService` layer, establishing clean system-wide packet forwarding to secure proxy tunnels.
  
- **🔒 Secure HTTPS Proxy Support**
  Native support for encrypted HTTPS and secure upstream proxy protocols, efficiently processing SSL handshake payloads and remote credentials.

- **📋 Smart Clipboard Import**
  Eliminates tedious manual input. Integrates an intelligent parsing routine that instantly detects, decodes, and applies proxy server nodes straight from the clipboard string.

- **⚡ Modern Architecture**
  Built on modern Android development paradigms targeting API Level 34 (Android 14) using stable Gradle workflows (`:app:assembleDebug`).

---

## 🛠️ Architecture & Setup

### Requirements
- **Minimum Android SDK:** 26+
- **Target Android SDK:** 34 (Android 14)
- **IDE:** AndroidIDE or Android Studio (Gradle 8.0.0+)

### Quick Build
To compile the debug artifact locally from your terminal, execute:
```bash
./gradlew :app:assembleDebug
