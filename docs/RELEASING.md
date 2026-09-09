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
