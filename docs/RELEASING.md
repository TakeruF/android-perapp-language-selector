# Release artifacts

This project has two independent distribution channels. Do not mix their artifacts.

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
`com.takeruf.perapplocale.debug`. Version 1.0.5 (versionCode 9) starts a separate Google Play app.
Previous releases used `dev.takeru.perapplocale`, so Android installs this version as a separate app
and does not migrate the old selector's preferences or Shizuku permission. Grant Shizuku access to
the new app after installation. Existing target-app locale settings are managed by Android.
Use the existing store descriptions and assets for the new listing, and confirm its package by
uploading the signed release AAB.

The canonical product website is `https://takeruf.com/en/projects/per-app-language` and the
privacy policy is `https://takeruf.com/en/projects/per-app-language/privacy`. The previous
GitHub Pages policy remains available for older installed versions.
