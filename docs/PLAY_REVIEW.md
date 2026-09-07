# Google Play review preparation

This document is the source for the Play Console App content and Permissions Declaration entries. It does not submit or publish anything.

## App access

No sign-in is required. The app is usable without an account. The write path requires the separately installed Shizuku service and the user's explicit authorization.

Reviewer steps:

1. Install and open Per-App Language.
2. On first launch, read the installed-app access notice and choose **Continue**. Choosing **Not now** leaves the installed-app inventory unread and the list empty.
3. Open **Shizuku** and start its service using Wireless debugging, or use a rooted device with Sui.
4. Return to Per-App Language and grant its Shizuku permission.
5. Select a third-party app, choose a language such as English (`en-US`) or Simplified Chinese (`zh-CN`), and choose **Apply**.
6. Reopen the target app to observe the system per-app locale. **Apply & Restart** also requests a force-stop and relaunch where the device permits it.
7. Select **System Default** to remove the override.

The app's list and read-only documentation are available even when Shizuku is not running. Without Shizuku, changing another app's locale is intentionally unavailable.

## QUERY_ALL_PACKAGES declaration

**Core functionality:** app search and interoperability with any installed application.

**Why it is required:** the primary screen is a user-facing picker for any installed package. Package names are not known in advance, and valid targets may have no launcher activity or intent filter. Finite `<queries>` declarations or intent-based queries cannot provide the same picker and would make the core feature incomplete.

**Data used:** package names, labels, icons, current per-app locale settings, and official `LocaleConfig` declarations are read only to render the picker, show language compatibility, and perform the user's selected locale action.

**Data handling:** the inventory is processed on-device and is not transmitted, sold, shared, used for advertising, or used for analytics. The selected package name and locale tag are passed only to the locally installed and user-authorized Shizuku service for the requested operation.

## Prominent disclosure

The first-run access notice explains what installed-app information is read, why the list is required, that processing remains on-device, and that no information is sent over the internet. The user must choose **Continue** before the app reads the installed-app inventory. **Not now** and back/dismiss leave the inventory unread.

## Data safety

- No data is collected off-device.
- No data is shared with third parties by Per-App Language.
- Installed-app inventory and locale assignments are processed or stored locally only.
- There is no account creation, advertising SDK, analytics SDK, or Internet permission.
- Local preferences are removed when the user clears app storage or uninstalls the app.

## Privacy policy

Use the publicly accessible, non-geofenced URL below in Play Console and keep it synchronized with the repository policy:

<https://takeruf.github.io/android-perapp-language-selector/privacy-policy.html>

The same URL is linked from the in-app Help screen.

## Technical review notes

- Package: `dev.takeru.perapplocale`
- Minimum Android version: Android 13 / API 33
- Target API: 36
- Release version in this checkout: `1.0.3` / version code `6`
- The release artifact uses Shizuku for the user-authorized system-service call; it does not install, update, or modify other APKs.
- Locale writes are user-triggered for the selected package and user profile. There is no background locale scanning or remote command path.
