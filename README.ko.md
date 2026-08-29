# Per-App Language

[English](README.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | 한국어 | [Español](README.es.md) | [Français](README.fr.md)

## 다운로드

[<img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/main/badge_github.png" alt="Get it on GitHub" height="60">](https://github.com/TakeruF/android-perapp-language-selector/releases/latest)

**Google Play:** 현재 비공개 테스트 중이며 곧 출시될 예정입니다.

모든 릴리스는 같은 키로 서명되며 인증서 지문은 릴리스 노트에 공개됩니다. 개인정보 처리 내용은 [개인정보 처리방침](PRIVACY_POLICY.md)을 참고하세요.

---

## 앱 소개

앱 내부에 언어 설정이 없고 *설정 → 앱 → 앱 언어*에 표시되지 않는 앱에도 특정 **로캘** 을 강제로 적용하는 Android 유틸리티입니다.

기기 시스템 언어는 한국어로 유지하면서 WeChat과 Taobao는 중국어 간체(简体中文),
ChatGPT는 영어, Google 지도는 한국어로 각각 설정해 사용할 수 있습니다.

> **번역 앱이 아닙니다.**
> 앱에 전달되는 로캘만 변경합니다. 앱에 해당 언어 리소스가 없으면 화면은 바뀌지 않습니다. [지원하지 않는 기능](#지원하지-않는-기능)을 참고하세요.

---

## 스크린샷

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/list.png" alt="앱별 로캘 목록" width="100%"><br><sub><b>설치된 모든 앱</b><br>변경된 앱을 표시하고 맨 위로 정렬</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/picker.png" alt="언어 선택기" width="100%"><br><sub><b>언어 선택</b><br>기본 항목, 내 언어 또는 BCP 47 태그</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/setup.png" alt="Shizuku 설정 안내" width="100%"><br><sub><b>Shizuku 설정</b><br>4단계, 루팅과 컴퓨터 불필요</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/help.png" alt="도움말 화면" width="100%"><br><sub><b>도움말</b><br>기능과 한계</sub></td>
  </tr>
</table>

---

## 실제 작동 방식

Android 13에는 **앱별 로캘** 기능이 추가되었습니다. 시스템은 패키지별 `LocaleList` 재정의를 저장하고 앱이 시작될 때 해당 `Configuration`에 적용합니다. 프레임워크가 구성을 직접 재정의하므로 앱이 이 기능을 별도로 채택하지 않아도 됩니다.

앱이 직접 지원해야 하는 부분은 *목록에 표시되는 것*뿐입니다. *설정 → 앱 → 앱 언어*에는 `locales_config.xml`(`android:localeConfig`)을 포함한 앱만 나타납니다. 이 파일이 없는 앱은 설정에 보이지 않아도 내부 재정의는 정상적으로 작동합니다.

이 앱은 설치된 모든 패키지에 같은 재정의를 직접 기록합니다.

---

## 요구 사항

| | |
|---|---|
| Android | 13(API 33) 이상 |
| 루팅 | 필요 없음 |
| [Shizuku](https://shizuku.rikka.app/) | 필요(루팅 기기는 Sui 사용 가능) |

### Android 13 이상이 필요한 이유

앱별 로캘은 Android 13에 추가된 플랫폼 기능입니다. `LocaleManager`, `"locale"` 시스템 서비스와 패키지별 `LocaleList` 저장소는 API 33 이전에 존재하지 않으며 Android 12 이하에서 대체할 방법도 없습니다.

### Shizuku가 필요한 이유

공개 API인 `LocaleManager.setApplicationLocales(LocaleList)`는 **호출한 패키지** 의 로캘만 변경합니다. 내부 시스템 서비스에는 패키지를 지정하는 다음 메서드가 있습니다.

```aidl
// frameworks/base/core/java/android/app/ILocaleManager.aidl
void setApplicationLocales(String packageName, int userId, in LocaleList locales);                          // API 33
void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate);    // API 34+
LocaleList getApplicationLocales(String packageName, int userId);
```

`LocaleManagerService`는 호출자가 대상 패키지 자체이거나 다음 권한을 보유한 경우에만 이 호출을 허용합니다.

* `android.permission.CHANGE_CONFIGURATION` — 로캘 쓰기
* `android.permission.READ_APP_SPECIFIC_LOCALES` — 로캘 읽기
* `android.permission.FORCE_STOP_PACKAGES` — *적용 후 다시 시작*

세 권한 모두 `signature|privileged`이므로 일반 앱에는 부여할 수 없습니다. 하지만 `com.android.shell`(uid 2000)은 세 권한을 모두 선언하며, 그래서 `adb shell cmd locale set-app-locales …`가 작동합니다.

Shizuku는 같은 shell uid로 작은 서비스를 실행하고 앱의 Binder 트랜잭션을 중계합니다. 따라서 이 앱이 권한을 직접 얻는 것이 아니라 shell uid가 대신 호출하며, 루팅도 필요하지 않습니다.

### 전체 앱 목록이 필요한 이유

핵심 기능은 패키지 이름을 미리 알 수 없는 **모든 설치된 앱** 을 선택하는 것입니다. Android의 제한된 패키지 쿼리로는 이 목록을 만들 수 없어 `QUERY_ALL_PACKAGES`를 선언합니다. 가져온 패키지 이름, 표시 이름, 아이콘, 로캘 설정과 공식 언어 선언은 기기 안에서만 사용합니다. 인터넷 권한이 없으며 앱 목록을 공유하지 않습니다. [개인정보 처리방침](PRIVACY_POLICY.md)을 참고하세요.

---

## 설정

1. **Shizuku 설치** — [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api), F-Droid 또는 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases)에서 설치합니다.

2. **Shizuku 서비스 시작**

   *기기에서(Android 11 이상, 컴퓨터 불필요):* 개발자 옵션 → **무선 디버깅** 켜기 → Shizuku 열기 → **무선 디버깅으로 시작** .

   *컴퓨터에서:*
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   Shizuku는 재부팅하면 중지되므로 이 단계를 다시 수행해야 합니다. 루팅된 기기에서는 Shizuku 대신
   자동으로 시작되는 **Sui** 를 설치해 사용할 수 있습니다.

3. **Per-App Language를 열고** Shizuku 권한 요청을 허용합니다.

4. 앱과 언어를 선택한 뒤 **적용 후 다시 시작** 을 누릅니다.

> **설정 후:** Shizuku가 중지되거나 기기를 재부팅하거나 개발자 옵션을 꺼도 Android는 적용된 언어 설정을 유지합니다. 개발자 옵션과 디버깅은 Shizuku를 시작하고 사용 가능한 상태로 유지할 때만 필요합니다. 언어를 변경하거나 초기화하려면 다시 켜고 Shizuku를 시작하세요. 루팅되지 않은 기기에서는 재부팅할 때마다 이 시작 과정이 필요합니다. Shizuku를 계속 실행하려면 [문제 해결 안내](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-shizuku-randomly-stops)에서 권장하는 대로 개발자 옵션과 USB 디버깅을 켜 두세요.

---

## 로캘 변경이 적용되는 과정

```
Per-App Language (uid 10xxx)
        │  ServiceManager.getService("locale")      ← 원시 Binder, 아직 권한 없음
        ▼
  ShizukuBinderWrapper                              ← Parcel 중계
        │
        ▼
Shizuku server process (uid 2000 = com.android.shell)
        │  CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES / FORCE_STOP_PACKAGES 보유
        ▼
LocaleManagerService  →  패키지별 LocaleList 재정의  →  다음 앱 시작 시 적용
```

앱은 서비스와 통신하는 두 가지 방식을 구현하며 자동으로 대체 경로를 사용합니다.

1. **`ILocaleManager$Stub.asInterface` 리플렉션** (기본). 기기의 메서드를 검사해 API 33 → 34 시그니처 변경(`fromDelegate`)과 OEM 변경에 대응합니다.
2. **직접 구현한 `Parcel` 트랜잭션** (대체). 숨겨진 API 리플렉션이 차단될 때 사용합니다. 숨겨진 클래스는 필요 없지만 API 33 이후 유지된 AIDL 선언 순서의 트랜잭션 ID를 사용합니다.

**적용** 은 로캘만 씁니다. **적용 후 다시 시작** 은 `IActivityManager.forceStopPackage()`를 호출하고 앱을 다시 엽니다. 많은 앱이 시작 시 문자열을 캐시하고 실행 중 구성 변경을 무시하기 때문입니다.

**시스템 기본값** 을 선택하면 빈 `LocaleList`를 보내 재정의를 제거합니다.

---

## 지원하지 않는 기능

* **번역할 수 없습니다.** 영어 리소스만 있는 앱에 `zh-CN`을 설정해도 화면은 바뀌지 않으며 Android는 앱의 기본 리소스로 대체합니다.
* **자체적으로 언어를 선택하는 앱** 은 시스템 로캘을 무시할 수 있습니다. 언어를 계정, 서버 또는 앱 내부 설정에 저장하는 일부 중국 슈퍼앱이 해당합니다.
* **프로세스 시작 시에만 로캘을 읽는 앱** 에는 *적용 후 다시 시작*이 필요합니다.
* **앱 내부 웹 콘텐츠** (WebView, 서버 렌더링 화면)는 일반적으로 앱별 로캘이 아니라 계정 또는 `Accept-Language`를 따릅니다.
* 일부 OEM 시스템에서는 **강제 종료가 거부될 수 있습니다.** 로캘은 기록되므로 앱을 직접 닫으면 되며 UI에서 이를 안내합니다.
* **업무 프로필 / 보조 사용자:** 본 앱이 설치된 사용자 영역(프로필) 내에서만 설정을 변경할 수 있습니다.
* 크게 수정된 일부 OEM ROM은 AOSP보다 shell 권한을 더 제한할 수 있습니다. 이 경우 앱은 조용히 실패하지 않고 `SecurityException`을 표시합니다.

---

## 호환성

모든 기능은 표준 AOSP 인터페이스인 `"locale"` 및 `"activity"` 시스템 서비스, `PackageManager`, Shizuku를 사용합니다. OEM별 분기나 기기 허용 목록이 없으므로 AOSP, Pixel, One UI, ColorOS, OriginOS, HyperOS에서 같은 방식으로 작동합니다. 제조사가 기능을 제한하면(주로 강제 종료) 기기별 우회 대신 오류를 표시합니다.

---

## 검증

특권 계층의 동작은 단순한 추측이나 모의 구현(mock)에 의존하지 않고 실제 Android 환경에서
엄격히 검증했습니다. `app/src/debug/.../LocaleGatewayProbe.kt`는 실제 `LocaleGateway`와
`ProcessGateway`를 uid 2000의 `app_process`로 실행해 Shizuku나 mock 없이 기기의
`LocaleManagerService`에 직접 연결합니다.

```
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
```

리플렉션 경로와 원시 트랜잭션 경로를 모두 실행하고 `cmd locale get-app-locales`와 결과를 대조했습니다. API 37 에뮬레이터에서 9개 검사가 통과하여 트랜잭션 ID, Parcel 레이아웃과 `fromDelegate` 인수를 확인했습니다.

같은 에뮬레이터에서 실제 Shizuku 13.6.0으로 상태 변화(미설치 → 권한 필요 → 준비됨), 권한 대화상자, 타사 앱에 `zh-CN` 적용, 강제 종료 및 재실행, 시스템 기본값 복원을 엔드투엔드로 검증했습니다. 각 결과는 UI가 아닌 `cmd locale get-app-locales`로 확인했습니다.

단, 사용할 수 있었던 Android 버전은 API 37뿐이므로 API 33의 인수 3개 `setApplicationLocales` 분기는 AOSP 소스에 근거하며 실제 실행되지는 않았습니다. 실행된 것은 API 34 이상의 인수 4개 분기입니다.

고정된 OEM 교차 테스트 절차와 Pixel/AOSP, One UI, ColorOS, HyperOS의 현재 검증 상태는
[`docs/OEM_SMOKE_TEST.md`](docs/OEM_SMOKE_TEST.md)에서 관리합니다.

---

## 구조

```
app/src/main/java/dev/takeru/perapplocale/
├── PerAppLocaleApp.kt          Application; 숨겨진 API 제한 해제, ShizukuRepository 소유
├── MainActivity.kt             단일 Activity, Compose 호스트, 이벤트 연결
├── core/
│   ├── SystemBinder.kt         서비스 조회 + ShizukuBinderWrapper
│   ├── LocaleGateway.kt        get/setApplicationLocales — 리플렉션 + 원시 트랜잭션
│   └── ProcessGateway.kt       IActivityManager를 통한 forceStopPackage
├── shizuku/
│   ├── ShizukuState.kt         READY / PERMISSION_REQUIRED / NOT_RUNNING / NOT_INSTALLED
│   └── ShizukuRepository.kt    Binder + 권한 리스너를 StateFlow로 노출
├── data/
│   ├── AppInfo.kt              목록의 한 행
│   ├── AppRepository.kt        PackageManager 조회
│   ├── LocaleOption.kt         프리셋 + BCP 47 검증
│   └── SettingsStore.kt        DataStore 환경설정 + 할당 내역의 로컬 미러
├── ui/                         Compose(Material 3) 화면과 MainViewModel
│   └── theme/Theme.kt          동적 색상, 라이트/다크 테마
└── util/AppIcon.kt             지연 로딩, LRU 캐시 앱 아이콘
```

`MainViewModel`은 Shizuku 상태, 패키지 목록과 DataStore 설정을 하나의 `MainUiState`로 결합합니다. UI는 이 상태의 순수 함수이며 스낵바와 앱 실행 같은 일회성 이벤트는 별도 채널로 전달됩니다.

로캘의 기준은 시스템입니다. DataStore는 Binder 검색이 끝나기 전에도 설정된 앱을 즉시 표시하기 위한 미러만 보관합니다.

---

## 빌드

```
git clone https://github.com/TakeruF/android-perapp-language-selector.git
cd android-perapp-language-selector
./gradlew assembleDebug
```

JDK 17과 Android SDK 36이 필요합니다. Android Studio에서 열면 바로 가져올 수 있습니다.

### 릴리스 빌드

프로젝트 루트에 `keystore.properties`가 있으면 `assembleRelease`가 APK에 서명합니다. 파일이 없어도 빌드는 성공하며 서명되지 않은 APK를 생성합니다.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

이 파일과 키 저장소는 모두 Git에서 제외됩니다.

---

## 피드백 및 기여

번역 오류를 발견하면 [번역 오류 Issue 템플릿](https://github.com/TakeruF/android-perapp-language-selector/issues/new?template=translation-error.yml)을 사용해 제보해 주세요.
[버그 제보](https://github.com/TakeruF/android-perapp-language-selector/issues)와
[Pull Request](https://github.com/TakeruF/android-perapp-language-selector/pulls)도 환영합니다.

---

## 감사의 말

* **[VegaBobo/Language-Selector](https://github.com/VegaBobo/Language-Selector)** — Shizuku를 통한 앱별 로캘 제어가 가능함을 보여 준 선행 프로젝트입니다. 일반적인 접근 방식만 참고했으며 이 프로젝트는 AOSP 소스와 Shizuku API를 기반으로 독립 구현되었고 해당 코드를 포함하지 않습니다. 향후 코드를 포함한다면 Apache-2.0 저작자 표시를 정식으로 추가합니다.
* **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** 및 Shizuku API.
* **[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)** .

## 라이선스

Apache License 2.0 — [LICENSE](LICENSE)를 참고하세요.
