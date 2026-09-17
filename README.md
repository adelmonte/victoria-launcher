# Victoria Launcher

An open source alternative to [Niagara Launcher](https://niagaralauncher.app) — a
minimal, list-based Android home screen.

![Victoria Launcher](docs/banner2.png)

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/dev.victorialauncher/)

Or grab the APK from [Releases](../../releases). Both carry the same signature,
so you can move between them without uninstalling.

Once it is installed, pick Victoria Launcher under
**Settings → Apps → Default apps → Home app**.

Requires Android 8.0 (API 26) or newer.

## Getting started

The home screen starts almost empty on purpose — everything is added by you.

- **Long-press the wallpaper** for the main menu: add favorites, add a widget,
  edit the layout, or open settings.
- **Swipe in from either edge** of the screen for the full A-Z app list, and
  slide your thumb along the letters to jump straight to one. Tap the edge and
  let go to just open the list.
- **Long-press any app** — on the home screen or in the A-Z list — to rename it,
  change its icon, hide it, or move it into a folder.
- **Edit layout** turns on drag handles for reordering and steppers for every
  gap, height and margin, including how far the A-Z strip reaches.

Worth knowing about in **Settings**:

- Alignment and icon side, set separately for favorites and the A-Z list
- A search box in the app list, at the top or the bottom
- Sorting each letter by how often you open its apps
- Swipe up from the home screen to open the app list
- Swipe left or right below your favorites to launch a chosen app
- Now Playing controls, which need notification access
- Double-tap the A-Z strip to lock the screen, which needs an accessibility
  service — if that toggle is greyed out, open App info and allow restricted
  settings first (Android blocks it for apps installed outside a store)

Work profiles are picked up automatically. A private space needs Android 15 or
later and this app set as your default home app; while it is unlocked its apps
are listed like any others, and a "Private space" padlock entry locks and
unlocks it.

## Changelog

Per-release notes live in
[fastlane/metadata/android/en-US/changelogs](fastlane/metadata/android/en-US/changelogs),
and are shown on each [GitHub release](../../releases) and on the app's F-Droid
page.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

Translations are very welcome — see [TRANSLATING.md](docs/TRANSLATING.md).

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, and [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together.

## License

[GPL-3.0-or-later](LICENSE)
