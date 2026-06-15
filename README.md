# 🚀 ShareProxy

An advanced Android network utility that allows you to easily share your phone's active VPN or TUN connection with your laptop, PC, or other devices over a local Wi-Fi Hotspot via a built-in HTTP/HTTPS proxy server.

![Android Target](https://img.shields.io/badge/Android-SDK%2034-brightgreen?style=flat-square&logo=android)
![Feature](https://img.shields.io/badge/VPN-Tethering%20%2F%20Sharing-blue?style=flat-square)
![Build](https://img.shields.io/badge/AGP-8.0.0-orange?style=flat-square)

---

## ✨ Key Features

- **🌐 VPN-over-Hotspot Sharing**
  Bypass Android's native restriction that blocks VPN traffic when turning on a Hotspot. Share your active VPN/TUN connection with any laptop or PC seamlessly.

- **🔒 Built-in HTTP/HTTPS Proxy Server**
  Spuns up a local proxy server right on your Android device, routing client traffic through the secure mobile VPN interface.

- **📋 Smart Clipboard Configuration**
  Instantly import, parse, and apply proxy server configurations or credentials from your system clipboard with a single click.

- **⚡ Modern & Optimized**
  Fully compatible with Android 14 (API Level 34), ensuring high-speed data forwarding, low battery consumption, and stable background services.

---

## 💻 How It Works (Laptop Connection)

1. **Turn on Hotspot:** Enable Portable Hotspot on your Android phone and connect your laptop to it.
2. **Start ShareProxy:** Open the app on your phone, ensure your VPN is connected, and click **Start Server**. It will show your phone's local IP (e.g., `192.168.43.1`) and a Port (e.g., `8080`).
3. **Configure Laptop:** Go to your Laptop's Network Settings -> Proxy Settings, enable "Use a proxy server", and enter the IP and Port provided by the app.
4. **Enjoy Open Internet:** Your laptop's traffic is now securely tunneled through your phone's VPN!

---

## 🛠️ Architecture & Requirements

- **Minimum SDK:** 28 (Android 9.0)
- **Target SDK:** 34 (Android 14)
- **Build Toolchain:** Gradle 8.0.0+ / AndroidIDE

To compile the project manually:
```bash
./gradlew :app:assembleDebug

- **📋 Smart Clipboard Import**
  Eliminates tedious manual input. Integrates an intelligent parsing routine that instantly detects, decodes, and applies proxy server nodes straight from the clipboard string.

- **⚡ Modern Architecture**
  Built on modern Android development paradigms targeting API Level 34 (Android 14) using stable Gradle workflows (`:app:assembleDebug`).

---

## 🛠️ Architecture & Setup

### Requirements
- **Minimum Android SDK:** 28+
- **Target Android SDK:** 34 (Android 14)
- **IDE:** AndroidIDE or Android Studio (Gradle 8.0.0+)

### Quick Build
To compile the debug artifact locally from your terminal, execute:
```bash
./gradlew :app:assembleDebug
