# Per-App Language

English | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

An Android utility that forces a specific **locale** on individual apps — even apps that have no
in-app language setting and never show up under *Settings → Apps → App language*.

Your phone can stay in Japanese while WeChat and Taobao run in 简体中文, ChatGPT runs in English,
and Google Maps stays Japanese.

> **This is not a translator.**
> It changes the locale an app *sees*. If the app does not ship resources for that language,
> nothing visible will change. See [What this cannot do](#what-this-cannot-do).

---

## Screenshots

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/list.png" alt="App list with per-app locales" width="100%"><br>
      <sub><b>Every installed app</b><br>Overridden apps are marked and can be floated to the top</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/picker.png" alt="Language picker" width="100%"><br>
      <sub><b>Pick a language</b><br>Presets, your own languages, or any BCP 47 tag you type</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/setup.png" alt="Shizuku setup guide" width="100%"><br>
      <sub><b>Shizuku setup</b><br>Four steps, no root, no computer required</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/help.png" alt="Help screen" width="100%"><br>
      <sub><b>Help</b><br>What it does, and where it stops</sub>
    </td>
  </tr>
</table>

---

## What it actually does

Android 13 introduced **per-app locales**. The system keeps a per-package `LocaleList` override and
applies it to that app's `Configuration` when the app starts. Apps do not have to opt in for this
to work — the framework overrides the configuration regardless.

What apps *do* have to opt in to is being *listed*: *Settings → Apps → App language* only shows
apps that ship a `locales_config.xml` (`android:localeConfig`). Apps without it are invisible in
Settings even though the underlying override works perfectly.

This app writes that same override directly, for any installed package.

---

## Requirements

| | |
|---|---|
| Android | 13 (API 33) or newer |
| Root | not required |
| [Shizuku](https://shizuku.rikka.app/) | required (or Sui on rooted devices) |

### Why Android 13+

Per-app locales are a platform feature added in Android 13. `LocaleManager`, the `"locale"` system
service and the per-package `LocaleList` store simply do not exist before API 33, and there is no
comparable mechanism to emulate them on Android 12 and earlier.

### Why Shizuku

The public API, `LocaleManager.setApplicationLocales(LocaleList)`, only ever writes the **calling**
package's locales. The system service behind it exposes a package-scoped variant:

```aidl
// frameworks/base/core/java/android/app/ILocaleManager.aidl
void setApplicationLocales(String packageName, int userId, in LocaleList locales);                          // API 33
void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate);    // API 34+
LocaleList getApplicationLocales(String packageName, int userId);
```

`LocaleManagerService` allows those calls when the caller either owns the target package or holds:

* `android.permission.CHANGE_CONFIGURATION` — to write a locale
* `android.permission.READ_APP_SPECIFIC_LOCALES` — to read one
* `android.permission.FORCE_STOP_PACKAGES` — for *Apply & Restart*

All three are `signature|privileged`, so a normal app can never be granted them. But
`com.android.shell` (uid 2000) declares all three in its manifest — which is exactly why
`adb shell cmd locale set-app-locales …` works.

Shizuku runs a small service as that same shell uid and lets an app route binder transactions
through it. So this app does not gain any permission itself; it asks the shell uid to make the
call on its behalf. That is also the reason nothing here needs root.

---

## Download

Grab the signed APK from the
[latest release](https://github.com/TakeruF/android-perapp-language-selector/releases/latest).
Every release is signed with the same key; the certificate fingerprint is published in the
release notes.

Privacy details are available in the [Privacy Policy](PRIVACY_POLICY.md).

---

## Setup

1. **Install Shizuku** — [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api),
   F-Droid, or [GitHub releases](https://github.com/RikkaApps/Shizuku/releases).

2. **Start the Shizuku service.**

   *On-device (Android 11+, no computer needed):*
   Developer options → **Wireless debugging** on → open Shizuku → **Start via Wireless debugging**.

   *From a computer:*
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   Shizuku dies on reboot. This step has to be repeated after every restart.
   Rooted users can install **Sui** instead, which starts automatically.

3. **Open Per-App Language** and grant the Shizuku permission prompt.

4. Tap an app, pick a language, tap **Apply & Restart**.

> **After setup:** Android keeps the applied language setting even if Shizuku stops, the device
> reboots, or Developer options are turned off. Developer options and debugging are only needed to
> start and keep Shizuku available; turn them back on and start Shizuku whenever you want to change
> or reset a language. On non-rooted devices, that startup step is required after every reboot. If
> you want Shizuku to remain continuously available, its
> [troubleshooting guide](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-shizuku-randomly-stops)
> recommends leaving Developer options and USB debugging enabled.

---

## How a locale change is applied

```
Per-App Language (uid 10xxx)
        │  ServiceManager.getService("locale")      ← raw binder, no permission yet
        ▼
  ShizukuBinderWrapper                              ← re-routes the parcel
        │
        ▼
Shizuku server process (uid 2000 = com.android.shell)
        │  holds CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES / FORCE_STOP_PACKAGES
        ▼
LocaleManagerService  →  per-package LocaleList override  →  applied on next app start
```

The app implements two ways of speaking to that service, and falls back automatically:

1. **Reflection on `ILocaleManager$Stub.asInterface`** (primary). It inspects the method it finds
   on the device, so it adapts by itself to the API 33 → 34 signature change (`fromDelegate`) and
   to OEM tweaks.
2. **Hand-written `Parcel` transactions** (fallback), used when hidden-API reflection is blocked.
   It needs no hidden class at all, at the price of hard-coding transaction ids — which have kept
   the same AIDL declaration order since API 33.

**Apply** writes the locale only. **Apply & Restart** additionally calls
`IActivityManager.forceStopPackage()` and relaunches the app, because a locale change reaches a
running app as a configuration change, and many apps cache their strings at startup and ignore it.

Selecting **System Default** sends an empty `LocaleList`, which is how the framework spells
"remove the override".

---

## What this cannot do

* **It cannot translate.** Setting `zh-CN` on an app that only ships English resources changes
  nothing visible. Android falls back to the app's default resources.
* **Apps that pick their own language internally** (a language stored in the account/server, or
  their own in-app setting) will ignore the system locale. Some Chinese super-apps do this.
* **Apps that re-read the locale only at process start** need *Apply & Restart* — that is what
  the button is for.
* **Web content inside an app** (WebViews, server-rendered screens) usually follows the account or
  `Accept-Language`, not the per-app locale.
* **Force-stop may be refused** on some OEM builds. The locale is still written; you just have to
  close the app yourself. The UI says so when this happens.
* **Work profiles / secondary users**: the app operates on the user it is installed in.
* Some heavily modified OEM ROMs may restrict shell permissions further than AOSP does. In that
  case the app reports the `SecurityException` instead of silently failing.

---

## Compatibility

Everything here goes through standard AOSP interfaces — the `"locale"` and `"activity"` system
services, `PackageManager`, and Shizuku. There is no per-OEM branching and no device
model allowlist, which is what should let it work on AOSP, Pixel, One UI, ColorOS, OriginOS and
HyperOS alike. Where a vendor restricts something (most commonly force-stop), the app surfaces the
failure rather than working around it with device-specific hacks.

---

## Verification

The privileged layer is not taken on trust. `app/src/debug/.../LocaleGatewayProbe.kt` runs the real
`LocaleGateway` and `ProcessGateway` classes as uid 2000 via `app_process`, straight against the
live `LocaleManagerService` — no Shizuku, no mocks:

```
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
```

It exercises the reflection path *and* the raw-transaction path, and cross-checks every result
against `cmd locale get-app-locales`. On an API 37 emulator all nine checks pass, which confirms
the hard-coded transaction ids, the parcel layout and the `fromDelegate` argument are right.

Verified end-to-end on that emulator with Shizuku 13.6.0 actually running: state transitions
(not installed → permission needed → ready), the permission dialog, applying `zh-CN` to a
third-party app, force-stop-and-relaunch, and resetting back to System Default — each time
confirming the result with `cmd locale get-app-locales` rather than trusting the UI.

One caveat, stated plainly: the only Android version available here was API 37, so the API 33
three-argument `setApplicationLocales` branch is derived from the AOSP sources rather than executed.
The four-argument branch (API 34+) is the one that ran.

---

## Architecture

```
app/src/main/java/dev/takeru/perapplocale/
├── PerAppLocaleApp.kt          Application; lifts hidden-API restrictions, owns ShizukuRepository
├── MainActivity.kt             Single activity, Compose host, event plumbing
├── core/
│   ├── SystemBinder.kt         Service lookup + ShizukuBinderWrapper
│   ├── LocaleGateway.kt        get/setApplicationLocales — reflection + raw-transaction paths
│   └── ProcessGateway.kt       forceStopPackage via IActivityManager
├── shizuku/
│   ├── ShizukuState.kt         READY / PERMISSION_REQUIRED / NOT_RUNNING / NOT_INSTALLED
│   └── ShizukuRepository.kt    Binder + permission listeners exposed as a StateFlow
├── data/
│   ├── AppInfo.kt              One row
│   ├── AppRepository.kt        PackageManager queries
│   ├── LocaleOption.kt         Presets + BCP 47 validation
│   └── SettingsStore.kt        DataStore: preferences + local mirror of assignments
├── ui/                         Compose (Material 3): MainScreen, LocaleSheet,
│   │                           ShizukuStatusCard, MainViewModel, and two prose
│   │                           screens — SetupScreen, HelpScreen — built from DocScreen
│   └── theme/Theme.kt          Dynamic color where available; light + dark
└── util/AppIcon.kt             Lazy, LRU-cached launcher icons
```

Unidirectional data flow: `MainViewModel` combines the Shizuku state, the package list and
DataStore preferences into one `MainUiState`; the UI is a pure function of it, and one-shot things
(snackbars, launching an app) travel over a separate event channel.

The system is the source of truth for locales — DataStore only keeps a mirror so the list can show
configured apps instantly before the (per-package) binder scan finishes.

---

## Building

```
git clone https://github.com/TakeruF/android-perapp-language-selector.git
cd android-perapp-language-selector
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK 36. Open in Android Studio and it should just import.

### Release builds

`assembleRelease` signs the APK when a `keystore.properties` sits in the project root; without
it the build still succeeds and produces an unsigned APK.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

The file and the keystore are both gitignored.

---

## Acknowledgements

* **[VegaBobo/Language-Selector](https://github.com/VegaBobo/Language-Selector)** — prior art that
  demonstrated per-app locale control over Shizuku is feasible. It was consulted as a reference
  for the general approach; this project is an independent implementation written against the
  AOSP sources and the Shizuku API, and contains no code from it. (Should any code ever be
  incorporated, its Apache-2.0 attribution will be added here formally.)
* **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** and the Shizuku API.
* **[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)**.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
