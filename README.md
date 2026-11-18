# Rain Frog

A personal, rain-first Android weather app for one phone (Columbia City, OR).
Sideloaded as an APK; never published to a store. The full spec and the
decisions behind it are in [CLAUDE.md](CLAUDE.md).

## Build

1. Copy `local.properties.example` to `local.properties` and fill it in.
   That file is gitignored: this repository is public, so keys and the NWS
   contact email never go in git. Without `pirateWeatherApiKey` the app still
   works: the rain-timing line then comes from Open-Meteo's 15-minute data.
   To add the key without it showing on screen or in shell history, run
   `./scripts/set-pirate-key.sh` and paste it when asked.
2. Once only: run `./scripts/create-signing-key.sh` to make the personal
   signing key (it asks for a password, shown nowhere). Android installs an
   update only if it is signed with the same key, so back up the key file
   (`~/.rain-frog/rain-frog-release.p12`) and its password. Without the key,
   builds use Android's default debug key, which is tied to this Mac.
3. `./gradlew testDebugUnitTest` runs the unit tests.
4. `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the phone

Turn on USB debugging on the phone (Settings → About phone → tap "Build
number" seven times, then Settings → System → Developer options → USB
debugging), connect it, and run:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` updates the installed app and keeps its data. The app has no location
or notification permissions to grant.

Test builds (`./gradlew assembleDebug -PtestHooks`, emulator only) accept a
sample-weather scenario for checking each frog look:
`adb shell am start -n dev.corbyoliver.weather/.MainActivity --es scenario CLEAR_NIGHT`
(RAIN, WINDY, COLD_SNAP, CLEAR_NIGHT, CLEAR_DAY, CLOUDY), and
`--es pinWidget temp|alert|rain` to add a widget. The phone's APK is built
without `-PtestHooks`, so other apps cannot make it show invented weather.

## Credits

Forecast: Open-Meteo (CC BY 4.0) · Rain timing: Pirate Weather · Alerts:
National Weather Service. DM Sans font: SIL Open Font License, see
`licenses/DM-Sans-OFL.txt`.
