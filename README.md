# Grip Gains Auto

An unofficial Android companion app for [gripgains.ca](https://gripgains.ca/) that adds quality-of-life features.

## Features

- **Keep screen awake** - Screen stays on during your training session
- **Auto-click fail button** - Automatically clicks the fail button when your grip fails
- **Measure actual weight** - Verify your load before inputting on the website (ensures consistency when switching setups or using cable machines where indicated weight may differ from actual load)
- **Target weight feedback** - Set a target weight and get visual, audio, and haptic feedback when you're off target
- **Real-time force graph** - Visualize your grip force over time with a live chart
- **End-of-set summary** - View detailed rep statistics after each set
- **Background timer** - Keep the timer running accurately while the app is backgrounded
- **Debug log viewer** - Inspect and share diagnostic logs when troubleshooting

### Experimental

- **Auto-set target weight** - Automatically sets the website's target weight picker from measured weight or your configured manual target

## Requirements

- Android 10 (API 29)+
- Supported devices (optional - the app works without one if you just want to keep the screen awake):
  - Tindeq Progressor
  - PitchSix
  - WHC06

## Installation

1. Clone this repository
2. Change to the Android project directory: `cd android/grip_gains_companion`
3. Build with `./gradlew assembleDebug` or `make assemble`
4. Install the generated APK on your Android device

## Disclaimer

This is an unofficial app and is not affiliated with, endorsed by, or connected to Grip Gains or Tindeq in any way.

## License

MIT License - see [LICENSE](LICENSE) for details.
