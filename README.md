# Rain Frog

A personal, rain-first Android weather app for one phone (Columbia City, OR).
Sideloaded as an APK; never published to a store. The full spec and the
decisions behind it are in [CLAUDE.md](CLAUDE.md).

## Build

1. Copy `local.properties.example` to `local.properties` and fill it in.
   That file is gitignored: this repository is public, so keys and the NWS
   contact email never go in git.
2. `./gradlew testDebugUnitTest` runs the unit tests.
3. `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.

Debug builds accept a sample-weather scenario for checking each frog look:
`adb shell am start -n dev.corbyoliver.weather/.MainActivity --es scenario CLEAR_NIGHT`
(RAIN, WINDY, COLD_SNAP, CLEAR_NIGHT, CLEAR_DAY, CLOUDY).

## Credits

Forecast: Open-Meteo (CC BY 4.0) · Rain timing: Pirate Weather · Alerts:
National Weather Service. DM Sans font: SIL Open Font License, see
`licenses/DM-Sans-OFL.txt`.
