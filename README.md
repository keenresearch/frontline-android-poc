# KeenASR Voice-Picking Proof of Concept

A proof-of-concept Android application demonstrating the use of [KeenASR SDK](https://keenresearch.com) for voice-directed warehouse picking operations. This app showcases on-device automatic speech recognition (ASR) with support for Bluetooth headsets commonly used in industrial environments.

## Overview

This PoC demonstrates three listening modes to help evaluate which approach works best for your workflow:

### Listening Modes

1. **Tap-to-Talk** - User taps a button to start listening. Recognition stops automatically after Voice Activity Detection (VAD) thresholds are met. Best for noisy environments where you want explicit control over when the device listens.

2. **Always-On Listening** - The app listens continuously and automatically restarts after each recognition. Ideal for hands-free operation in controlled environments with minimal background speech.

3. **Trigger Phrase** - Combines always-on listening with a wake word ("Hey Computer"). The recognizer only processes commands after hearing the trigger phrase. Useful when operators need to have conversations without triggering commands.

> **Note:** A production voice-picking application would typically use only one mode, most likely **Always-On Listening** for maximum hands-free efficiency.

## Features

- **On-device speech recognition** - No internet connection required
- **Bluetooth headset support** - Automatically routes audio through connected BT headsets with SCO (Synchronous Connection-Oriented) audio
- **Customizable command vocabulary** - Easily modify recognized phrases in `MainActivity.getPhrases()`
- **Real-time partial results** - See recognition results as the user speaks

## Supported Commands

The app recognizes the following voice commands:
- Navigation: `NEXT`, `PREVIOUS`, `BACK`, `DONE`
- Actions: `PICK`, `SKIP`, `PRINT`, `START`, `STOP`, `PAUSE`, `RESUME`, `CANCEL`, `CLEAR`
- Digits: `ZERO` through `NINE`, `O` (letter O, commonly used for zero)
- Utility: `HELP`

These commands can be updated in the `getPhrases` method of the `MainActivity` class.

## Requirements

- Android 8.1 (API 27) or higher
- Microphone permission
- Bluetooth permissions (for headset support)

## Building

```bash
# Build debug APK
./gradlew build

# Install to connected device
./gradlew installDebug
```

## Project Structure

- `app/` - Main application module
  - `MainActivity.java` - Primary UI and ASR controller
  - `bluetooth/BluetoothStatusReceiver.java` - Bluetooth headset connection and SCO audio routing
  - `Constants.java` - Listening mode constants
- `KeenASR/` - Pre-built KeenASR SDK (AAR library)

## Trial SDK Limitation

This app ships with a trial version of KeenASR SDK that will exit 15 minutes after launch. For a full license without this limitation, contact info@keenresearch.com.

## Licensing

By cloning this repository and using the trial KeenASR SDK, you agree to the [Trial SDK Licensing Agreement](https://keenresearch.com/keenasr-docs/keenasr-trial-sdk-licensing-agreement.html).

## Resources

- [Frontline Workers Use Case](https://keenresearch.com/keenasr-docs/keenasr-dev-use-case-frontline.html)
- [KeenASR Documentation](https://keenresearch.com/keenasr-docs)
- [Contact for Licensing](mailto:info@keenresearch.com)

## Support

For questions about the SDK or custom ASR bundles optimized for your hardware, contact info@keenresearch.com.
