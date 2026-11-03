# Cinematic TV — Phase 1

Lightweight, Apple TV‑inspired launcher for Android TV (min API 28). Pure Kotlin + framework Views.
No AndroidX, no WebView, no analytics, no ads. Network is used only for the daily photos.

## Build
Requirements: JDK 17, Android SDK platform 34 + build-tools 34 (`local.properties` → `sdk.dir`).
```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

## Install / run
```
adb connect 10.0.0.6:5555
adb -s 10.0.0.6:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10.0.0.6:5555 shell am start -n com.cinematictv.launcher/.MainActivity
```
It also appears in the normal Android TV app list (LEANBACK_LAUNCHER).

## Screens
- **Home** — top shelf shows the focused app's artwork; Dock of favourites; round buttons for Apps, Search, Inputs, Notifications, Control Centre; clock top-right.
- **Apps** — alphabetical grid, search-as-you-type, ★ marks apps in the Dock.
- **Inputs** — side sheet: Live TV, HDMI, AV with connection status. Also opened by the system "view inputs" action.
- **Control Centre** — side sheet: Wi-Fi, Sound, Date & Time, Notifications, All Settings (entries the TV doesn't support are hidden).

## Photos & screensaver
- Daily landscape photos from Bing's public image archive (www.bing.com only; no account/key), refreshed every 12 h,
  cached on the TV (max 16, ~5 MB). Home shows them as a rotating backdrop (every 25 s); offline it falls back to app art.
- **Screensaver** ("Cinematic TV"): full-screen photos, slow pan/zoom, 20 s each, clock/date and caption.

Select it (or via Settings › Device Preferences › Screen saver):
```
adb -s 10.0.0.6:5555 shell settings put secure screensaver_components com.cinematictv.launcher/.CinematicDream
```
Restore Google's Ambient mode:
```
adb -s 10.0.0.6:5555 shell settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop
```
Preview now: `adb -s 10.0.0.6:5555 shell am start -n com.android.systemui/.Somnambulator`

## Remote controls
| Screen | Key | Action |
|---|---|---|
| Home | ◀ ▶ / ▲ | Move through Dock / up to buttons |
| Home | OK | Open app |
| Home | Hold OK (or Menu) | Edit Dock: tiles jiggle |
| Edit Dock | OK | Pick up / drop the focused tile; ◀ ▶ moves it |
| Edit Dock | Menu | Remove tile from Dock |
| Edit Dock | Back | Done (order saved) |
| Apps | OK / Hold OK | Open / add-or-remove from Dock |
| Apps | ▲ to search box, OK | Type to filter; Back returns to the grid |

## Make Cinematic TV the Home app (Google launcher disabled, not removed)
On this Philips TV the Google launcher's Home filter outranks the user preference, so it must be
disabled for user 0. Cinematic TV replaces what it provided:
- **Inputs** — own panel (Live TV / Composite / HDMI), also answers the Source key (`VIEW_INPUTS`)
- **Search** — Google's search app (`com.google.android.katniss`), unaffected
- **Notifications** — `com.google.android.tvrecommendations`, unaffected
- **Safety net** — if Cinematic TV ever fails, TV Settings' FallbackHome takes over

```
adb -s 10.0.0.6:5555 shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s 10.0.0.6:5555 shell cmd package set-home-activity com.cinematictv.launcher/.MainActivity
```
Not replaced: Google's recommendation rows/channels on the home screen. A system update may re-enable the Google launcher.

## Restore the Google TV launcher
```
adb -s 10.0.0.6:5555 shell pm enable com.google.android.tvlauncher
adb -s 10.0.0.6:5555 shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```
Uninstalling Cinematic TV (`adb -s 10.0.0.6:5555 uninstall com.cinematictv.launcher`) does **not** re-enable
the Google launcher — run `pm enable` first.

## Layout
- `AppRepository.kt` — dynamic app discovery (LEANBACK first, then LAUNCHER), icon/banner cache, safe launch
- `Prefs.kt` — ordered favourites in SharedPreferences; first-run seeding from installed apps
- `Tiles.kt` — rounded tile view holder + focus scale animation
- `MainActivity.kt` — home screen, clock/date, rearranging, Home/Back handling
- `AllAppsActivity.kt` — recycled GridView of all apps
- `Panel.kt` — shared side-sheet base
- `InputsActivity.kt` — Inputs sheet (TvInputManager, passthrough intents)
- `QuickSettingsActivity.kt` — Control Centre sheet
- `Photos.kt` — daily photo fetch, disk cache, downsampled decode
- `CinematicDream.kt` — screensaver
