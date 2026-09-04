# Per-App Language

[English](README.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md) | Français

## Téléchargement

[<img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/main/badge_github.png" alt="Get it on GitHub" height="60">](https://github.com/TakeruF/android-perapp-language-selector/releases/latest)

**Google Play :** Le test fermé est en cours. Prochainement disponible.

Toutes les versions sont signées avec la même clé ; l’empreinte du certificat figure dans les notes de version. Consultez la [Politique de confidentialité](PRIVACY_POLICY.md) pour plus de détails.

---

## Présentation

Un utilitaire Android qui impose des **paramètres régionaux** à chaque application, même si celle-ci ne propose aucun choix de langue et n’apparaît jamais dans *Paramètres → Applications → Langue de l’application*.

Votre téléphone peut rester en français tandis que WeChat et Taobao utilisent le chinois simplifié,
ChatGPT l’anglais et Google Maps le français.

> **Ce n’est pas un traducteur.**
> Il modifie les paramètres régionaux que l’application *voit*. Si elle ne contient pas de ressources dans cette langue, rien ne changera à l’écran. Consultez [Ce que l’application ne peut pas faire](#ce-que-lapplication-ne-peut-pas-faire).

---

## Captures d’écran

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/list.png" alt="Liste des applications avec leurs paramètres régionaux" width="100%"><br><sub><b>Toutes les applis installées</b><br>Les applis modifiées sont signalées et peuvent passer en tête</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/picker.png" alt="Sélecteur de langue" width="100%"><br><sub><b>Choisissez une langue</b><br>Suggestions, vos langues ou toute balise BCP 47</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/setup.png" alt="Guide de configuration de Shizuku" width="100%"><br><sub><b>Configurez Shizuku</b><br>Quatre étapes, sans root ni ordinateur</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/help.png" alt="Écran d’aide" width="100%"><br><sub><b>Aide</b><br>Fonctionnement et limites</sub></td>
  </tr>
</table>

---

## Fonctionnement réel

Android 13 a introduit les **paramètres régionaux par application**. Le système conserve un remplacement `LocaleList` pour chaque package et l’applique à sa `Configuration` au démarrage. L’application n’a pas besoin d’adopter cette fonction : le framework remplace sa configuration dans tous les cas.

Elle doit seulement l’adopter pour *figurer dans la liste* : *Paramètres → Applications → Langue de l’application* affiche uniquement les applications contenant `locales_config.xml` (`android:localeConfig`). Les autres restent invisibles dans les paramètres, même si le remplacement sous-jacent fonctionne parfaitement.

Cette application écrit directement ce même remplacement pour tout package installé.

---

## Prérequis

| | |
|---|---|
| Android | 13 (API 33) ou version ultérieure |
| Root | non requis |
| [Shizuku](https://shizuku.rikka.app/) | requis (ou Sui sur un appareil rooté) |

### Pourquoi Android 13+

Les paramètres régionaux par application sont une fonction de plateforme ajoutée dans Android 13. `LocaleManager`, le service système `"locale"` et le stockage `LocaleList` par package n’existent pas avant l’API 33, et aucun mécanisme comparable ne permet de les reproduire sous Android 12 ou une version antérieure.

### Pourquoi Shizuku

L’API publique `LocaleManager.setApplicationLocales(LocaleList)` modifie uniquement les paramètres régionaux du **package appelant**. Le service interne expose une variante qui reçoit un package :

```aidl
// frameworks/base/core/java/android/app/ILocaleManager.aidl
void setApplicationLocales(String packageName, int userId, in LocaleList locales);                          // API 33
void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate);    // API 34+
LocaleList getApplicationLocales(String packageName, int userId);
```

`LocaleManagerService` autorise ces appels si le processus appelant est lui-même le package cible
ou s’il détient :

* `android.permission.CHANGE_CONFIGURATION` — écrire des paramètres régionaux
* `android.permission.READ_APP_SPECIFIC_LOCALES` — les lire
* `android.permission.FORCE_STOP_PACKAGES` — pour *Appliquer et redémarrer*

Ces trois autorisations sont `signature|privileged` et ne peuvent donc pas être accordées à une application normale. En revanche, `com.android.shell` (uid 2000) les déclare toutes ; c’est pourquoi `adb shell cmd locale set-app-locales …` fonctionne.

Shizuku exécute un petit service avec ce même uid shell et permet à une application d’acheminer des transactions Binder par son intermédiaire. Cette application n’obtient donc aucune autorisation : elle demande à l’uid shell d’effectuer l’appel pour elle. C’est aussi pourquoi aucun root n’est nécessaire.

### Pourquoi la visibilité complète des applications est nécessaire

La fonction principale permet de choisir **n’importe quelle application installée**, dont le nom de package ne peut pas être connu à l’avance. Les requêtes ciblées d’Android ne peuvent pas produire cette liste ; l’application déclare donc `QUERY_ALL_PACKAGES`. Les packages, noms, icônes, paramètres régionaux et langues déclarées sont utilisés uniquement sur l’appareil ; l’application n’a pas d’autorisation Internet et ne partage pas cet inventaire. Consultez la [Politique de confidentialité](PRIVACY_POLICY.md).

---

## Configuration

1. **Installez Shizuku** depuis [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) ou [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).

2. **Démarrez le service Shizuku.**

   *Sur l’appareil (Android 11+, sans ordinateur) :* Options pour les développeurs → activez **Débogage sans fil** → ouvrez Shizuku → **Démarrer via le débogage sans fil**.

   *Depuis un ordinateur :*
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   Shizuku s’arrête lors d’un redémarrage ; cette étape doit alors être répétée. Les utilisateurs root peuvent installer **Sui**, qui démarre automatiquement.

3. **Ouvrez Per-App Language** et acceptez la demande d’autorisation Shizuku.

4. Touchez une application, choisissez une langue, puis **Appliquer et redémarrer**.

> **Après la configuration :** Android conserve le choix de langue même si Shizuku s’arrête, si l’appareil redémarre ou si les Options pour les développeurs sont désactivées. Ces options et le débogage ne servent qu’à démarrer Shizuku et à le maintenir disponible ; réactivez-les et relancez Shizuku pour modifier ou réinitialiser une langue. Sur un appareil non rooté, ce démarrage est nécessaire après chaque redémarrage. Pour garder Shizuku disponible en continu, son [guide de dépannage](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-shizuku-randomly-stops) recommande de laisser les Options pour les développeurs et le débogage USB activés.

---

## Application d’un changement de paramètres régionaux

```
Per-App Language (uid 10xxx)
        │  ServiceManager.getService("locale")      ← Binder brut, aucune autorisation à ce stade
        ▼
  ShizukuBinderWrapper                              ← réachemine le Parcel
        │
        ▼
Processus serveur Shizuku (uid 2000 = com.android.shell)
        │  détient CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES / FORCE_STOP_PACKAGES
        ▼
LocaleManagerService  →  remplacement LocaleList par package  →  appliqué au prochain démarrage
```

L’application propose deux moyens de communiquer avec ce service et bascule automatiquement sur le second :

1. **Réflexion sur `ILocaleManager$Stub.asInterface`** (principal). Elle inspecte la méthode présente sur l’appareil afin de s’adapter au changement de signature entre les API 33 et 34 (`fromDelegate`) et aux modifications des fabricants.
2. **Transactions `Parcel` écrites manuellement** (secours), utilisées si la réflexion sur les API cachées est bloquée. Elles n’utilisent aucune classe cachée, au prix d’identifiants de transaction codés en dur dont l’ordre AIDL reste inchangé depuis l’API 33.

**Appliquer** écrit uniquement les paramètres régionaux. **Appliquer et redémarrer** appelle également `IActivityManager.forceStopPackage()` et rouvre l’application, car beaucoup d’applications mettent leurs chaînes en cache au démarrage et ignorent les changements de configuration en cours d’exécution.

Choisir **Valeur par défaut du système** envoie une `LocaleList` vide, ce qui demande au framework de supprimer le remplacement.

---

## Ce que l’application ne peut pas faire

* **Elle ne peut pas traduire.** Définir `zh-CN` sur une application qui ne contient que des ressources anglaises ne change rien ; Android revient aux ressources par défaut.
* **Les applications qui choisissent leur langue en interne** peuvent ignorer les paramètres régionaux du système. C’est le cas lorsque la langue est stockée dans le compte, sur le serveur ou dans un réglage interne, notamment pour certaines super-apps chinoises.
* **Les applications qui relisent les paramètres uniquement au démarrage** nécessitent *Appliquer et redémarrer*.
* **Le contenu web d’une application** (WebView ou écrans générés par un serveur) suit généralement le compte ou `Accept-Language`.
* Certains systèmes de fabricants peuvent refuser **l’arrêt forcé**. Les paramètres sont tout de même écrits ; il suffit de fermer l’application. L’interface vous le signale.
* **Profils professionnels et utilisateurs secondaires :** l’application n’agit que dans
  l’environnement utilisateur ou le profil où elle est installée ; la gestion entre profils n’est
  pas prise en charge.
* Certaines ROM fortement modifiées peuvent restreindre davantage les autorisations shell qu’AOSP. L’application affiche alors la `SecurityException` au lieu d’échouer silencieusement.

---

## Compatibilité

Tout passe par des interfaces AOSP standard : les services système `"locale"` et `"activity"`, `PackageManager` et Shizuku. Il n’existe aucune branche propre à un fabricant ni liste de modèles, ce qui permet un fonctionnement identique sur AOSP, Pixel, One UI, ColorOS, OriginOS et HyperOS. Lorsqu’un fabricant limite une opération — le plus souvent l’arrêt forcé — l’application affiche l’échec au lieu d’utiliser un contournement propre à l’appareil.

---

## Vérification

Le fonctionnement de la couche privilégiée a été validé directement avec les services système
Android réels, sans dépendre de mocks. `app/src/debug/.../LocaleGatewayProbe.kt` exécute les vraies
classes `LocaleGateway` et `ProcessGateway` avec l’uid 2000 via `app_process`, puis se connecte au
`LocaleManagerService` du système sans passer par Shizuku :

```
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
```

La sonde exécute la voie par réflexion et celle par transaction brute, puis compare chaque résultat avec `cmd locale get-app-locales`. Les neuf vérifications réussissent sur un émulateur API 37, confirmant les identifiants de transaction, la disposition du Parcel et l’argument `fromDelegate`.

Le parcours a aussi été vérifié de bout en bout sur cet émulateur avec Shizuku 13.6.0 réellement actif : changements d’état (non installé → autorisation requise → prêt), dialogue d’autorisation, application de `zh-CN` à une application tierce, arrêt et relance, puis retour aux paramètres du système. Chaque résultat a été confirmé avec `cmd locale get-app-locales`, sans se fier uniquement à l’interface.

Une réserve : la seule version Android disponible était l’API 37. La branche à trois arguments de `setApplicationLocales` pour l’API 33 provient donc du code source AOSP mais n’a pas été exécutée ; la branche à quatre arguments pour l’API 34+ l’a été.

La procédure inter-OEM fixe et l’état actuel des validations Pixel/AOSP, One UI, ColorOS et HyperOS
sont suivis dans [`docs/OEM_SMOKE_TEST.md`](docs/OEM_SMOKE_TEST.md).

---

## Architecture

```
app/src/main/java/dev/takeru/perapplocale/
├── PerAppLocaleApp.kt          Application ; lève les limites d’API cachées, possède ShizukuRepository
├── MainActivity.kt             Activité unique, hôte Compose et gestion des événements
├── core/
│   ├── SystemBinder.kt         Recherche de service + ShizukuBinderWrapper
│   ├── LocaleGateway.kt        get/setApplicationLocales — réflexion + transactions brutes
│   └── ProcessGateway.kt       forceStopPackage via IActivityManager
├── shizuku/
│   ├── ShizukuState.kt         READY / PERMISSION_REQUIRED / NOT_RUNNING / NOT_INSTALLED
│   └── ShizukuRepository.kt    Binder + écouteurs d’autorisation exposés par StateFlow
├── data/
│   ├── AppInfo.kt              Une ligne de la liste
│   ├── AppRepository.kt        Requêtes PackageManager
│   ├── LocaleOption.kt         Suggestions + validation BCP 47
│   └── SettingsStore.kt        DataStore : préférences + copie locale des affectations
├── ui/                         Écrans Compose (Material 3) et MainViewModel
│   └── theme/Theme.kt          Couleurs dynamiques, thèmes clair et sombre
└── util/AppIcon.kt             Icônes chargées à la demande avec cache LRU
```

`MainViewModel` combine l’état de Shizuku, la liste des packages et les préférences DataStore dans un `MainUiState`. L’interface est une fonction pure de cet état ; les événements ponctuels, tels que les messages et le lancement d’une application, passent par un canal séparé.

Le système est la source de vérité des paramètres régionaux. DataStore n’en conserve qu’une copie afin d’afficher immédiatement les applications configurées avant la fin de l’analyse Binder.

---

## Compilation

```
git clone https://github.com/TakeruF/android-perapp-language-selector.git
cd android-perapp-language-selector
./gradlew assembleDebug
```

JDK 17 et Android SDK 36 sont requis. Android Studio devrait importer directement le projet.

### Versions de publication

`assembleRelease` signe l’APK si un fichier `keystore.properties` se trouve à la racine du projet. Sans ce fichier, la compilation réussit tout de même et produit un APK non signé.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Ce fichier et le magasin de clés sont tous deux ignorés par Git.

---

## Retours et contributions

Si vous repérez une erreur de traduction, signalez-la à l’aide du [modèle d’Issue dédié aux erreurs de traduction](https://github.com/TakeruF/android-perapp-language-selector/issues/new?template=translation-error.yml).
Les [rapports de bugs](https://github.com/TakeruF/android-perapp-language-selector/issues) et les
[Pull Requests](https://github.com/TakeruF/android-perapp-language-selector/pulls) sont également les bienvenus.

---

## Remerciements

* **[VegaBobo/Language-Selector](https://github.com/VegaBobo/Language-Selector)** — projet antérieur ayant démontré la faisabilité du contrôle des paramètres régionaux par application via Shizuku. Il a servi de référence pour l’approche générale ; ce projet est une implémentation indépendante écrite à partir des sources AOSP et de l’API Shizuku, sans reprendre son code. Si du code devait être intégré à l’avenir, son attribution Apache-2.0 serait ajoutée formellement.
* **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** et l’API Shizuku.
* **[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)**.

## Licence

Apache License 2.0 — voir [LICENSE](LICENSE).
