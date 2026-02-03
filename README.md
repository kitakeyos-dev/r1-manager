# R1 Manager

Android application to extend and manage the Phicomm R1 smart speaker with voice assistant, music streaming, LED effects, and web-based device management.

## Features

### Voice Assistant (Xiaozhi)
- **Wake word detection** using Snowboy ("Alexa")
- **Voice Activity Detection (VAD)** with libfvad (WebRTC) + amplitude fallback
- **Opus audio codec** for efficient voice streaming (16kHz recording, 24kHz playback)
- **WebSocket protocol** for real-time voice communication
- **Multi-profile support** - switch between different voice backends
- **MCP integration** - AI can control device via tools

### Music Player
- **ExoPlayer-based** playback with HLS/DASH support
- **ZingMp3** Vietnamese music streaming integration
- **Playlist management** with queue, history, shuffle, repeat modes
- **Speed control** (0.5x - 2.0x)

### LED Control
- **15 internal LEDs** + **24 ring LEDs** hardware control
- **Music visualization** with 8 modes: Spectrum, Pulse, Wave, Rainbow, Party, Meteor, Vortex, Spiral
- **State-based feedback** - listening (green), speaking (bright)

### Device Management (Web UI)
- **App management**: Install/uninstall APKs, launch/stop apps
- **System monitoring**: RAM, storage, CPU usage
- **Volume control**: Music, ring, alarm, notification
- **Network**: WiFi scanning/connection, hotspot, Bluetooth
- **Shell commands** and logcat viewer
- **Wake-on-LAN** for remote device power control
- **File browser** with upload/download

### MCP Tools
AI can control the device through registered tools:
- `music_control` - Search/play music, control playback
- `get_ip` - Get device IP address
- `memory` - Store/retrieve conversation context
- `wol` - Wake remote devices on LAN

## Architecture

```
com.phicomm.r1manager/
├── MainActivity.java              # Entry point
├── WebServerService.java          # Background HTTP server
├── config/
│   ├── AppConfig.java             # App settings
│   └── XiaozhiConfig.java         # Voice bot profiles
├── server/
│   ├── WebServer.java             # NanoHTTPD + routing
│   ├── controller/                # 14 REST API controllers
│   ├── service/
│   │   ├── XiaozhiService.java    # Voice assistant
│   │   ├── ExoPlayerService.java  # Music player
│   │   └── MusicLedSyncService.java
│   ├── manager/
│   │   ├── XiaozhiAudioEngine.java
│   │   ├── LedManager.java
│   │   ├── ZingMp3Manager.java
│   │   └── MemoryManager.java
│   └── voicebot/
│       ├── OpusEncoder.java       # Native JNI
│       ├── OpusDecoder.java
│       ├── VadDetector.java       # libfvad wrapper
│       └── AudioRecorder.java
├── mcp/
│   ├── McpManager.java
│   ├── client/                    # WebSocket client
│   └── tools/                     # MCP tools
└── util/
    ├── AppLog.java
    ├── ThreadManager.java
    └── SecurityUtils.java
```

## Requirements

- **Target Device**: Phicomm R1 (Android 5.1) or Android 5.0+
- **Build**: Android SDK 30, NDK 27, Gradle 4.1
- **ABI**: armeabi-v7a
- **Required APK**: [`com.phicomm.speaker.player.apk`](com.phicomm.speaker.player.apk) must be installed on the device

## Building & Installation

### Quick Install (Recommended)

Use the included script to build release APK and install via ADB over WiFi:

```bash
# Edit R1_IP in install-release.bat to match your device IP
install-release.bat
```

The script will:
1. Build release APK
2. Connect to device via ADB over WiFi
3. Push and install APK
4. Launch the app

### Manual Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```
## API Endpoints

### Apps
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/apps` | List installed apps |
| POST | `/api/apps/install` | Install APK |
| POST | `/api/apps/{pkg}/launch` | Launch app |
| DELETE | `/api/apps/{pkg}` | Uninstall |

### System
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/system/info` | System info |
| GET | `/api/volume` | Get volumes |
| POST | `/api/volume` | Set volume |
| POST | `/api/system/shell` | Execute command |

### Music
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/playlist` | Get playlist |
| POST | `/api/zing/search` | Search ZingMp3 |
| POST | `/api/zing/play` | Play song |

### Voice Assistant
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/xiaozhi/status` | Get status |
| POST | `/api/xiaozhi/connect` | Connect to server |
| GET | `/api/xiaozhi/profiles` | List profiles |

### LED
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/music-led/status` | LED sync status |
| POST | `/api/music-led/mode` | Set visualization mode |

### Network
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/wifi` | WiFi info |
| GET | `/api/wifi/scan` | Scan networks |
| POST | `/api/wifi/connect` | Connect to network |

## Native Libraries

- **Opus 1.3.1** - Audio codec
- **libfvad** - WebRTC VAD
- **Snowboy** - Wake word detection

## License

[MIT License](LICENSE)
