# DC Proxy Share

Share your phone's VPN connection with other devices over the hotspot. The app runs a local HTTP and SOCKS5 proxy and includes a full network monitor.

## Features

- HTTP proxy and SOCKS5 proxy with a foreground service
- Home screen with live shared traffic and the devices using the proxy
- Network monitor
  - Overview: internet status, DNS time, public IP, active network, phone and proxy traffic
  - Wi-Fi: signal, link speed, band, channel, security, IP configuration, hotspot
  - VPN: interface, protocol, addresses, routes, exit IP and country
  - Devices: local network scan and proxy clients
  - Ping: live ping, jitter, loss, history chart, multi-target comparison
  - Logs: filterable event log with share and clear
- 8 themes in light and dark, wallpaper colors, pure black, icon shapes, font size, motion levels
- Persian and English

## Usage

1. Turn on your VPN and the phone hotspot.
2. Open the app and tap Start proxy.
3. On the other device, join the hotspot and set the proxy to the address shown on the Home screen.

## Build

The app has no native code, so one APK runs on arm64-v8a, armeabi-v7a, x86 and x86_64. The build also writes a universal APK and one APK per ABI.

Requirements: JDK 17 and Gradle 8.0 or newer.

```
gradle assembleDebug
gradle assembleRelease
```

APKs are written to `app/build/outputs/apk`.

### Signed release

The release and debug APKs are signed with `app/proxyshare.jks`. If the file is missing, Gradle generates it automatically before signing.

## GitHub Actions

The workflow in `.github/workflows/build.yml` builds the debug and release APKs on every push and pull request and uploads them as artifacts. Pushing a tag such as `v1.0` also publishes them as a GitHub release.

## Notes

- Android hides MAC addresses from apps on recent versions, so the device scan can show only IP addresses.
- The Wi-Fi name needs the location permission.
- The web check and public IP lookup use gstatic.com and ipwho.is and can be turned off in Settings.

## License

MIT License. Copyright (c) 2026 [@deepcodecreate](https://github.com/deepcodecreate). See [LICENSE](LICENSE).
