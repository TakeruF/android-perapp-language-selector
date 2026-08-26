# Privacy Policy for Per-App Language

Effective date: August 26, 2026

Per-App Language is developed by TakeruF. This policy explains how the app handles information.

## Information the app accesses

Per-App Language reads the list of applications installed on your Android device, including package names, app names, icons, current per-app locale settings, and official language-support declarations. This access is necessary so you can select any installed app, understand its declared language support, and view or change its language setting.

Android classifies the complete installed-app inventory as sensitive information. The app therefore uses `QUERY_ALL_PACKAGES` only for this visible, user-initiated core feature. A narrower package query cannot provide a picker for arbitrary installed apps because their package names are not known in advance.

The app also stores the following information locally on your device:

- display preferences, such as whether system apps are shown;
- package names and locale tags that you applied, so configured apps can be identified when the app starts.

## How information is used

The installed-app information and locally stored preferences are used only to display the app list, show declared language compatibility, and perform the language-setting actions you request. When you apply a language, the selected package name and locale tag are passed on the same device to the user-installed Shizuku service, which performs the requested Android system operation.

## Data collection and sharing

Per-App Language has no Internet permission and does not transmit information off your device. The developer does not collect, sell, or share personal or device data. The app contains no advertising or analytics SDKs.

Shizuku is a separate application that you choose to install and authorize. Communication with Shizuku occurs locally on your device. You can review Shizuku's project and documentation at <https://shizuku.rikka.app/>.

## Data retention and deletion

The app does not retain data on developer-operated servers. Local preferences remain on your device until you clear the app's storage or uninstall the app. Removing a per-app language assignment in Per-App Language removes that assignment from the app's local record and requests Android to reset the corresponding system locale override.

## Security

All processing performed by Per-App Language stays on the device. Access to Shizuku requires an explicit permission grant from you, which you can revoke in Shizuku at any time.

## Children

Per-App Language is intended for users aged 18 and over and is not directed to children.

## Changes to this policy

Material changes to this policy will be published in this repository with an updated effective date.

## Contact

For privacy questions or requests, contact the developer through the project's public issue tracker:
<https://github.com/TakeruF/android-perapp-language-selector/issues>
