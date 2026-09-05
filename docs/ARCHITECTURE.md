# Architecture

Victoria Launcher is a single-module Android home-screen launcher: Kotlin, Jetpack
Compose, `minSdk 26`, `compileSdk`/`targetSdk 35`. No network permission, no
analytics, no ads, no accounts.

## Package layout (`dev.victorialauncher`)

```
MainActivity.kt                 Activity, window setup, status-bar peek
VictoriaApp.kt                  Application; owns Prefs, repositories, widget host
data/
  AppInfo.kt                    component + label; `key` is the flattened component name
  AppRepository.kt              PackageManager queries, launch, app-info intents
  IconPackRepository.kt         icon-pack discovery, appfilter.xml parsing, icon lookup
  Prefs.kt                      DataStore<Preferences>: every setting, hidden apps, favorites
  Folder.kt                     folder model + JSON persistence + favorites token encoding
  HomePaddings.kt               per-block top/bottom spacing set in edit mode
ui/
  VictoriaNavHost.kt            collects settings once, hosts the navigation graph
  home/HomeRoute.kt             home destination: home screen, app-list overlay, edge zones
  home/HomeScreen.kt            widget slot + favorites/folders column + edit mode
  applist/AppListScreen.kt      A-Z list, pull-to-collapse, per-app menus
  applist/EdgeScrubber.kt       the A-Z strip that bows around the fingertip
  applist/EdgeTouchZone.kt      the invisible edge strip that opens and scrubs the list
  applist/ScrubState.kt         scrub state holder (see "Recomposition" below)
  applist/ScrubberGeometry.kt   one source of truth for where each letter sits
  applist/AppListModel.kt       flattens apps into headers + rows
  common/AppIcon.kt             icon rasterisation and the bounded bitmap cache
  common/…                      shared dialogs, icon picker, touch-position modifier
  settings/…                    settings screen and the three picker screens
  theme/                        Material theme, font mapping, wallpaper-aware text colour
media/                          notification-listener service + Now Playing card
service/                        haptics, status-bar fader, accessibility service, system UI
widget/                         AppWidgetHost, widget slot, widget picker
```

## Things that look wrong until you know why

**The launcher does not draw the wallpaper.** `Theme.VictoriaLauncher` sets
`android:windowShowWallpaper`, so the real system wallpaper shows through a
transparent window. "Dim wallpaper" is therefore just a scrim `Box`, not image
processing.

**`LongPressFrameLayout` wraps every embedded widget** instead of a Compose
`pointerInput`. A Compose long-press detector owns the whole touch stream once it
starts tracking, which would break the widget's own buttons. This mirrors how
scrollable containers arbitrate with children: don't intercept on `ACTION_DOWN`,
watch via `onInterceptTouchEvent`, steal the stream only once our timer fires.

**The A-Z strip bows; the letters never scale.** Scaling the glyphs stretched them
until they looked pixelated, so `EdgeScrubber` translates each letter along a
gaussian instead. `scrubY`/`pullPx` arrive as lambdas read inside `graphicsLayer`,
which puts the read in the draw phase — recomposing 26 `Text` nodes per pointer
move made this jitter.

**`recordTouchPosition` deliberately does not consume the down event.** It exists so
a row can place its long-press menu at the finger while `combinedClickable` and any
scrolling parent still arbitrate normally. Using `detectTapGestures` instead
consumes the down event and silently stops the parent from ever seeing the drag.

**The app-list overlay stays composed while hidden.** It is measured but never
placed (`layout(0, 0) {}`), so it neither draws nor receives touches. Building the
list from scratch on every open is what made it take a beat to appear. The home
screen behind it is likewise kept laid out — an `AppWidgetHostView` that is never
placed loses its layout and returns with its text collapsed.

**Gesture exclusion is spent on the scrub band, not the whole edge.** Android
honours only 200dp of exclusion per side, so `HomeRoute` clips the excluded
rectangle to the band the strip actually occupies.

## Recomposition

Two deliberate choices keep scrubbing off the main thread's back:

- **`ScrubState`** holds the letter, position and elastic pull. It is passed down as
  one stable object so only the composable that *reads* a field is invalidated. When
  the home destination read the letter directly, each of the ~26 letter changes per
  gesture invalidated that whole scope — and the home screen hangs off it.
- **Icons are rasterised once into a byte-bounded `LruCache`** and pre-warmed off the
  main thread, favorites first. The cache is bounded by bytes rather than entry
  count because the cache key includes the rasterised pixel size: moving the
  icon-size slider adds a generation rather than replacing one, and a single 96dp
  icon on a dense screen is ~450 KB.

## Permissions

The app requests **no `INTERNET` permission**. It cannot phone home.

| Permission | Why | Optional? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | A launcher must enumerate every installed app, icon pack and widget provider. Without it `PackageManager` filters almost everything out on Android 11+. | No |
| `EXPAND_STATUS_BAR` | Needed by the pre-Android-12 reflection fallback for opening the notification shade. | No |
| `VIBRATE` | The haptic tick as the finger crosses a letter. Respects the system touch-feedback setting, and can be turned off in Settings. | No |
| Accessibility service | The only sanctioned way to open the notification shade or lock the screen — neither has a public API. The service ignores every event it receives and reads nothing. | Yes — the swipe-down and double-tap-to-lock gestures simply report that they need it |
| Notification listener | Reads the active media session for the Now Playing card. | Yes — off by default |

`BIND_APPWIDGET` is **not** requested: it is signature-level and is never granted to
an ordinary app. `AppWidgetHost` and `bindAppWidgetIdIfAllowed` work without it for
whichever app is the current Home.

## Known gaps

- No search in the A-Z list; the edge scrubber is the only way to find an app.
- Work-profile apps are invisible. `AppRepository` uses `PackageManager` rather than
  `LauncherApps`, and `AppInfo` carries no `UserHandle`. Fixing this changes
  `AppInfo.key`, which is the persisted favorites key, so it needs a data migration.
- Double-tap-to-lock works only in the app list, not on the home screen.
- No Baseline Profile. Generating one needs a device or emulator; it is the largest
  remaining first-launch win.
- Unit tests cover only framework-free logic. Anything touching `PackageManager`,
  `ComponentName` or Compose needs Robolectric or an instrumented test.
