# [SACTL_] Signal - Android

**Provisioning shell for AI agent.**

A receive-only SMS Identity Node for AI agents. This app turns a spare Android phone into a secure relay that forwards encrypted OTPs to your cloud infrastructure.

## Features

- **Receive-Only**: No SMS sending capability (SEND_SMS removed)
- **E2E Encryption**: RSA encryption on-device before forwarding
- **Heartbeat Monitoring**: Battery, network status reporting
- **Zero-Trust Architecture**: Messages dropped if no encryption key configured

## Download

Get the latest APK from [Releases](../../releases).

## Quick Start

1. Install the APK on your Android device
2. Configure your RSA public key in Settings → Encryption
3. Set up webhook URL for receiving encrypted messages
4. Enable the service

## Build from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_ORG/Signal-Android.git
cd Signal-Android

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- Android 7.0 (API 24) or higher
- SMS permissions (RECEIVE_SMS, READ_SMS)
- Internet permission

## Architecture

```
SMS Received → Encrypt with RSA → Forward to Webhook
                    ↓
            No Key? DROP message (fail-safe)
```

## Security

- Messages are encrypted on-device using your RSA public key
- The app never stores plaintext messages
- No analytics or telemetry
- Open source for audit

## License

Apache 2.0 - See [LICENSE](LICENSE)

## Related Projects

- **Signal Hub** (Private): Backend API for managing Signal Nodes

---

**[SACTL_]** - Provisioning shell for AI agent.
