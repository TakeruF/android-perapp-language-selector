# Release artifacts

Per-App Language is publicly available through two distribution channels:

- [Google Play](https://play.google.com/store/apps/details?id=com.takeruf.perapplocale)
- Direct APKs through [GitHub Releases](https://github.com/TakeruF/android-perapp-language-selector/releases/latest) or [ShizuStore](https://shizustore.com/apps/android-perapp-language-selector)

ShizuStore links to the APKs published on GitHub; it does not host a separate build.
Both Google Play and direct APK distribution will continue to receive development and support.
Build the appropriate artifact for each channel as described below.

## Direct distribution

Build the APK intended for GitHub Releases:

```sh
./gradlew :app:packageDirectReleaseApk
```

Attach only this generated file to the GitHub Release:

```text
app/build/outputs/direct-release/per-app-language-v<versionName>.apk
```

The generic `app/build/outputs/apk/release/app-release.apk` is an intermediate Gradle output and
must not be uploaded. Verify the APK's package name, version, signing certificate, and SHA-256
before publishing it.

## Google Play

Build the Android App Bundle separately with `./gradlew :app:bundleRelease`, then upload the
result directly to Google Play Console. An `.aab` is an upload artifact, not an installable Android
package, so never attach it to a public GitHub Release.

## Package migration (1.0.5)

The release application ID and Kotlin namespace are `com.takeruf.perapplocale`; debug builds use
`com.takeruf.perapplocale.debug`. Version 1.0.5 (versionCode 9) introduced the current package.
Versions 1.0.4 and earlier used `dev.takeru.perapplocale`, so Android installs the current package
as a separate app and does not migrate the old selector's preferences or Shizuku permission.
Grant Shizuku access to the current app after installation. Existing target-app locale settings
are managed by Android. Publish future Play updates to the existing `com.takeruf.perapplocale`
listing.

The canonical product website is `https://takeruf.com/en/projects/per-app-language` and the
privacy policy is `https://takeruf.com/en/projects/per-app-language/privacy`. The previous
GitHub Pages policy remains available for older installed versions.
