# OEM smoke-test matrix

Run this matrix on every supported OEM before a public release. A successful build or an AOSP-only
test is not evidence for another manufacturer's firmware.

## Fixed user flow

Use a normal launchable app that is safe to stop, record its original per-app locale, and then:

1. Open Per-App Language Selector and confirm that Shizuku is **Ready**.
2. Read the target app's current locale.
3. Select a different declared language and tap **Apply & Restart**.
4. Confirm that the target app relaunches and displays the selected language.
5. Return to Per-App Language Selector and confirm that the selected locale is still shown.
6. Select **System Default**, apply it, and confirm that the original state is restored.
7. Copy the diagnostics block from **Help → About** and attach it to the test result.

Also run the debug probe below. It independently exercises both the reflection and raw Binder
paths, checks locale reads and writes against Android's command-line result, and resets the target
to System Default before it exits:

```sh
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
adb shell cmd locale get-app-locales com.android.settings
```

The final command must report an empty locale list. If the test target had a non-default locale
before testing, restore that exact value instead of using the probe's default target.

## Evidence matrix

| Platform | Device / API | Read | Apply | Restart | Verify | Reset | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Google / AOSP | `sdk_gphone16k_arm64`, Android 17 / API 37 | Pass | Pass | Pass | Pass | Pass | 2026-08-26: Shizuku 13.6.0; debug probe 9/9; final locale `[]` |
| Samsung One UI | Not available | Pending | Pending | Pending | Pending | Pending | Requires a Samsung device |
| OPPO ColorOS | Not available | Pending | Pending | Pending | Pending | Pending | Requires an OPPO device |
| Xiaomi HyperOS | Not available | Pending | Pending | Pending | Pending | Pending | Requires a Xiaomi device |

Do not turn a pending row into “supported” based only on code review or another OEM's result.
