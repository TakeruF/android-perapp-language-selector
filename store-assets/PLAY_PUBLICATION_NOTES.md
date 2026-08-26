# Google Play publication copy and consistency checklist

This file is the canonical wording for the app's use of broad package visibility. Keep the Play
Console declaration, store listing, in-app Help, README, and Privacy Policy aligned with it.

## Core-purpose statement for the store listing

Per-App Language lets users browse any application installed on their device, inspect the
languages that application officially declares, and view or change its Android per-app locale.
The user selects the target application and language for every operation.

## `QUERY_ALL_PACKAGES` Permissions Declaration

**Core functionality:** App search and interoperability with any installed application.

**Why broad visibility is required:** The app's primary user-facing screen is a picker containing
all installed applications. Package names are not known in advance, and users must be able to
select applications that expose no launcher activity or intent filter. Finite `<queries>` entries
or intent-based queries would omit valid targets and make the core feature unusable.

**How the data is used:** Package names, application labels, icons, current per-app locale settings,
and official `LocaleConfig` declarations are read only to render the picker, show language
compatibility, and carry out the user's locale action.

**Data handling:** Installed-app inventory remains on the device. The app has no Internet
permission and contains no advertising or analytics SDK. It does not collect, sell, or share app
inventory data. The selected package name and locale tag are sent only to the locally installed,
user-authorized Shizuku service for the requested Android operation.

## Reviewer evidence

- Video: `store-assets/query-all-packages-demo.mp4`
- The video should show the complete app list, selection of a third-party app, its declared
  languages, and the locale picker.
- Reviewer instructions must explain how to install/start Shizuku and grant this app permission.

## Data safety consistency

- No data is collected or shared off-device.
- Installed-app inventory is accessed and processed ephemerally on-device.
- Display preferences and the local mirror of package-to-locale assignments remain on-device and
  are deleted by clearing app storage or uninstalling the app.
- Privacy Policy: `PRIVACY_POLICY.md`

## Before each submission

- Confirm the permission is still necessary for the core picker and no narrower API can provide
  equivalent functionality.
- Update the Permissions Declaration if the use of installed-app information changes.
- Confirm the store description prominently describes browsing and changing any installed app.
- Confirm the Privacy Policy URL serves the current repository text publicly.
- Re-record reviewer evidence if the relevant UI or workflow changes materially.
