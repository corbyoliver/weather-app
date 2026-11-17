# Personal Weather App (Android, sideloaded)

A personal, rain-first weather app for one user, installed as an APK (never published to the Play Store). Home location: Columbia City, OR (ZIP 97018) at 45.8901, -122.8071 (confirmed; see Decisions). Units: °F and mph.

## Working rules
- Build in small steps and commit often. Run and test each step before moving on.
- Before writing any API client, fetch the provider's current docs and confirm endpoints, parameters and limits. APIs change.
- No secrets in git. API keys go in `local.properties` and are read via `BuildConfig`.
- Ask before adding any paid service or new dependency that is not listed here.
- Keep UI state, domain models and API models separate (see Architecture).
- Write unit tests for the API-to-domain mappers and for the frog-state selector.

## Tech stack
Kotlin, Jetpack Compose (Material 3), Retrofit or Ktor with kotlinx.serialization, WorkManager for background refresh, Room or DataStore for the cache, Jetpack Glance for home-screen widgets. Confirm the phone's Android version before choosing minSdk.

## Data sources (all free for personal use)
| Source | Used for | Refresh | Notes |
|---|---|---|---|
| Open-Meteo | Backbone: current, hourly, 7-day, humidity, wind, air quality, sunrise/sunset | About every 30 min via WorkManager (~48 calls/day) | Free for non-commercial use, up to 10,000 calls/day, no API key. Data is CC BY 4.0, so show attribution. |
| NWS API (api.weather.gov) | Official US alerts | Poll every 15-30 min to keep the banner and alert widget current (no Android notifications) | `/alerts/active?point=lat,lon`. Send a descriptive `User-Agent` (app name + contact email). US-only. |
| Pirate Weather | Short-term rain timing ("rain eases in ~25 min") | On app open; cache 5-10 min | Free API key. Free-tier limits came from secondary sources, so verify on their docs. Dark Sky-style JSON. |

Failure handling:
- If Pirate Weather fails, fall back to Open-Meteo hourly/15-minute precipitation.
- If NWS fails, show "Alerts unavailable", never "No alerts" (a silent failure could hide a real warning).
- Cache the last good response from each source so the app opens instantly and works offline. Show "Updated <time>".

Attribution footer: "Forecast: Open-Meteo (CC BY 4.0) · Rain timing: Pirate Weather · Alerts: National Weather Service".

## Architecture
One `WeatherRepository` between the UI and three small clients (OpenMeteoClient, PirateWeatherClient, NwsClient). Each client maps its API response into our own domain models, so a provider can be swapped without touching screens. Background jobs: forecast refresh (30 min) and alert poll (15-30 min, updates the cached alerts that the banner and alert widget show; it never posts a notification).

## UI spec
Design mockups live on a private design canvas (the link is in the repo README if the user adds it). Claude Code cannot open that link, so everything needed is here. The primary direction is "Home v2": a dark tonal layout with a rain-first hero.

Screen order, top to bottom:
1. **Alert banner** (only when an NWS alert is active): amber pill, "Wind Advisory until 9 PM · NWS", tappable for details.
2. **Hero card** (very rounded, 32dp): "Now" + location, large temperature (about 80sp) with condition icon, condition text and "Feels like". A one-line rain-timing sentence in the accent color (for example "Rain eases in ~25 min · returns around 8 PM"). Hourly row of 6 columns: temperature on top, icon, time below. An illustrated frog scene fills the bottom of the card (see Frog).
3. **Day chips**: horizontally scrolling, one per day (day name, icon, hi/lo). The selected chip has a light outline. Selecting a day updates the detail rows below.
4. **Expandable detail rows** with a rounded icon tile, title and value, plus a chevron: Precipitation (expanded by default; hourly rain-chance bars), Wind (hourly speed with direction arrows), then plain rows for Humidity and Air quality.
5. **Footer** with attribution.

Home-screen widgets (Glance): small 2x2 temperature + rain chance, small 2x2 alert widget (amber), and a wide 4x2 rain-timing widget with a mini 60-minute rain strip.

Design tokens (dark theme):
- Background `#1B1D25`, card `#272A35`, tonal tile `#323644`, divider `#2F3342`
- Text `#ECEEF4`, muted text `#A9AEBD`
- Accent (rain/data) `#8AB4F8`
- Alert amber `#F2B84B` with text `#1F1500`
- Font: DM Sans (400, 500, 700). Large temperature at weight 500.
- Touch targets at least 48dp. Text contrast at least 4.5:1. Real buttons with content descriptions on icon-only controls.

## Frog mascot (original character)
A small green Pacific tree frog drawn as an original illustration. It is our own character. Do not substitute any third-party mascot. Colors: body `#7BCB6A`, belly `#C9EBB8`, shading `#5DAE55`, leaf `#2E8B57`.

Six states, chosen in this priority order:
| Order | State | Trigger | Look |
|---|---|---|---|
| 1 | Rain | Rain chance next hour >= 40% | Under a leaf umbrella, rain streaks |
| 2 | Windy | Gusts >= 25 mph | Squinting, a leaf blowing away |
| 3 | Cold snap | Temp <= 34°F | Purple beanie, snow falling |
| 4 | Clear night | Night and cloud cover < 60% | Asleep under crescent moon and stars |
| 5 | Clear day | Day and cloud cover < 30% | Sunglasses, bright sun |
| 6 | Cloudy | Everything else | Calm and neutral under clouds |

These thresholds are starting suggestions and should be easy to change in one place. Build the frog as a vector drawable or Compose Canvas so it scales on the hero card and widgets.

## Build order
1. Project setup, theme and tokens, and a static home screen using sample data.
2. Open-Meteo client + repository + cache, wired to the hero, hourly row, day chips and detail rows.
3. Background refresh with WorkManager and the "Updated" timestamp.
4. NWS alerts: banner and polling (no notifications).
5. Pirate Weather rain-timing sentence and 60-minute rain strip, with the fallback.
6. Frog states driven by real data.
7. Home-screen widgets.
8. Polish: offline behavior, accessibility, attribution, and an install-ready debug APK.

## Decisions (answered by Corbett, 2026-11-17)
- **Phone: Android 16 (API 36).** `minSdk = 36` and `targetSdk = 36`. The app
  is for this one phone, so a lower minSdk would only add compatibility code
  paths that are never run or tested. targetSdk 36 matches the phone, so the
  app gets Android 16's behavior and nothing newer. `compileSdk = 37` because
  current AndroidX libraries need it to compile; it does not change runtime
  behavior. If the phone is replaced with an older one, lower minSdk then.
- **Fixed home location only.** Columbia City, OR 97018. No location permission
  and no "use my location" option. Coordinates **45.8901, -122.8071**: the
  Open-Meteo geocoder (GeoNames) gives this point for postcode 97018, and the
  NWS `/points` endpoint names it "Columbia City, OR" (office PQR, grid
  112,122, zone ORZ108, county ORC009). The spec's first guess, 46.10, is about
  23 km north, in Washington. Timezone `America/Los_Angeles`.
- **No notifications.** NWS alerts appear only in the in-app banner and the
  alert widget. The alert poll still runs so both stay current, but the app
  never posts an Android notification and never asks for the notification
  permission.
