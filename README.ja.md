# Per-App Language

[English](README.md) | [简体中文](README.zh-CN.md) | 日本語 | [한국어](README.ko.md) | [Español](README.es.md) | [Français](README.fr.md)

アプリごとに特定の**ロケール**を強制適用する Android ユーティリティです。アプリ内に言語設定がなく、
「設定 → アプリ → アプリの言語」に表示されないアプリにも対応しています。

端末自体は日本語のまま、WeChat と淘宝は简体中文、ChatGPT は英語、Google マップは日本語、
というように使い分けられます。

> **翻訳ツールではありません。**
> アプリから「見える」ロケールを変更します。アプリにその言語のリソースが含まれていなければ、
> 表示は何も変わりません。詳しくは[できないこと](#できないこと)を参照してください。

---

## スクリーンショット

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/list.png" alt="アプリごとのロケールを表示するアプリ一覧" width="100%"><br>
      <sub><b>インストール済みの全アプリ</b><br>変更済みのアプリには印が付き、先頭に表示できます</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/picker.png" alt="言語選択画面" width="100%"><br>
      <sub><b>言語を選択</b><br>プリセット、追加した言語、任意の BCP 47 タグを使用できます</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/setup.png" alt="Shizuku セットアップガイド" width="100%"><br>
      <sub><b>Shizuku のセットアップ</b><br>4 ステップ、root もパソコンも不要です</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/help.png" alt="ヘルプ画面" width="100%"><br>
      <sub><b>ヘルプ</b><br>できることと、その限界を説明します</sub>
    </td>
  </tr>
</table>

---

## 実際の動作

Android 13 では**アプリごとのロケール**が導入されました。システムはパッケージごとに
`LocaleList` の上書き設定を保持し、アプリの起動時にその `Configuration` へ適用します。
この動作にアプリ側の対応は必要ありません。フレームワークがアプリの対応状況にかかわらず設定を
上書きします。

アプリ側の対応が必要なのは、システム設定に**表示されること**だけです。
「設定 → アプリ → アプリの言語」には `locales_config.xml`（`android:localeConfig`）を
同梱したアプリしか表示されません。このファイルがないアプリは設定画面には現れませんが、
基盤となるロケールの上書き機能自体は問題なく動作します。

このアプリは、任意のインストール済みパッケージに同じ上書き設定を直接書き込みます。

---

## 必要環境

| | |
|---|---|
| Android | 13（API 33）以降 |
| Root | 不要 |
| [Shizuku](https://shizuku.rikka.app/) | 必要（root 済み端末では Sui も利用可能） |

### Android 13 以降が必要な理由

アプリごとのロケールは Android 13 で追加されたプラットフォーム機能です。API 33 より前には
`LocaleManager`、`"locale"` システムサービス、パッケージ単位の `LocaleList` ストアが
存在せず、Android 12 以前で代替できる同等の仕組みもありません。

### Shizuku が必要な理由

公開 API の `LocaleManager.setApplicationLocales(LocaleList)` が書き込めるのは、
**呼び出し元自身**のパッケージのロケールだけです。その背後のシステムサービスには、
パッケージを指定できる次の API があります。

```aidl
// frameworks/base/core/java/android/app/ILocaleManager.aidl
void setApplicationLocales(String packageName, int userId, in LocaleList locales);                          // API 33
void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate);    // API 34+
LocaleList getApplicationLocales(String packageName, int userId);
```

`LocaleManagerService` がこれらの呼び出しを許可するのは、呼び出し元が対象パッケージ自身であるか、
次の権限を持っている場合です。

* `android.permission.CHANGE_CONFIGURATION` — ロケールの書き込み
* `android.permission.READ_APP_SPECIFIC_LOCALES` — ロケールの読み取り
* `android.permission.FORCE_STOP_PACKAGES` — 「適用して再起動」で使用

3 つとも `signature|privileged` 権限なので、通常のアプリには付与できません。一方、
`com.android.shell`（uid 2000）は 3 つすべてをマニフェストで宣言しています。
`adb shell cmd locale set-app-locales …` が動作するのもそのためです。

Shizuku は同じ shell uid で小さなサービスを実行し、アプリが Binder トランザクションを
そのサービス経由で送れるようにします。つまり、このアプリ自体が権限を得るのではなく、
shell uid に呼び出しを代行してもらいます。root が不要なのもこの仕組みによるものです。

### 完全なアプリ一覧が必要な理由

コア機能は、パッケージ名を事前に知ることのできない**任意のインストール済みアプリ**を選ぶことです。
対象を限定したパッケージ照会ではこの一覧を作れないため、`QUERY_ALL_PACKAGES` を宣言しています。
取得したパッケージ名、表示名、アイコン、ロケール設定、公式の対応言語宣言は端末内だけで使用します。
インターネット権限はなく、アプリ一覧を共有しません。詳しくは[プライバシーポリシー](PRIVACY_POLICY.md)を参照してください。

---

## ダウンロード

[最新リリース](https://github.com/TakeruF/android-perapp-language-selector/releases/latest)から
署名済み APK をダウンロードしてください。すべてのリリースは同じ鍵で署名され、
証明書のフィンガープリントはリリースノートに掲載されます。

---

## セットアップ

1. **Shizuku をインストール** — [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)、
   F-Droid、または [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases)から
   インストールします。

2. **Shizuku サービスを起動します。**

   *端末だけで起動する場合（Android 11 以降、パソコン不要）：*
   開発者向けオプション → **ワイヤレス デバッグ**をオン → Shizuku を開く →
   **ワイヤレスデバッグで開始**を選択します。

   *パソコンから起動する場合：*
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```

   Shizuku は端末を再起動すると停止するため、再起動のたびにこの手順が必要です。
   root 済み端末では、自動起動する **Sui** も利用できます。

3. **Per-App Language を開き**、表示される Shizuku の権限ダイアログで許可します。

4. アプリをタップして言語を選び、**適用して再起動**をタップします。

> **セットアップ後：**Shizuku が停止したり、端末を再起動したり、開発者向けオプションを
> オフにしたりしても、適用済みの言語設定は Android に保持されます。開発者向けオプションと
> デバッグが必要なのは、Shizuku を起動して利用可能な状態に保つためだけです。言語を変更または
> リセットするときは、再びオンにして Shizuku を起動してください。root 化していない端末では、
> 再起動するたびにこの起動手順が必要です。Shizuku を常時利用できる状態にしたい場合、
> [トラブルシューティングガイド](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-shizuku-randomly-stops)では、
> 開発者向けオプションと USB デバッグをオンのままにすることが推奨されています。

---

## ロケール変更が適用される仕組み

```
Per-App Language (uid 10xxx)
        │  ServiceManager.getService("locale")      ← raw Binder、この時点では権限なし
        ▼
  ShizukuBinderWrapper                              ← Parcel の送信先を変更
        │
        ▼
Shizuku サーバープロセス (uid 2000 = com.android.shell)
        │  CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES / FORCE_STOP_PACKAGES を保有
        ▼
LocaleManagerService  →  パッケージ単位の LocaleList 上書き  →  次回のアプリ起動時に適用
```

このアプリはサービスと通信する方法を 2 つ実装し、自動的にフォールバックします。

1. **`ILocaleManager$Stub.asInterface` のリフレクション**（優先）。端末上で見つかった
   メソッドを調べるため、API 33 → 34 のシグネチャ変更（`fromDelegate`）や OEM 独自の
   調整に自動で対応します。
2. **手書きの `Parcel` トランザクション**（フォールバック）。hidden API のリフレクションが
   ブロックされた場合に使います。hidden クラスが一切不要になる代わりに、トランザクション ID を
   ハードコードします。ただし、その AIDL 宣言順は API 33 以降変わっていません。

**適用**はロケールの書き込みだけを行います。**適用して再起動**はさらに
`IActivityManager.forceStopPackage()` を呼び出してアプリを再起動します。ロケール変更は
実行中のアプリには構成変更として通知されますが、多くのアプリは起動時に文字列をキャッシュし、
その通知を無視するためです。

**システムのデフォルト**を選ぶと、空の `LocaleList` が送られます。これはフレームワークにおける
「上書き設定を削除」の表現です。

---

## できないこと

* **翻訳はできません。**英語リソースしか含まないアプリに `zh-CN` を設定しても、
  表示は何も変わりません。Android はアプリのデフォルトリソースにフォールバックします。
* **独自に言語を選ぶアプリ**（言語をアカウントやサーバーに保存するもの、独自のアプリ内設定を
  持つもの）は、システムのロケールを無視します。一部の中国系スーパーアプリが該当します。
* **プロセス起動時にしかロケールを読み直さないアプリ**では「適用して再起動」が必要です。
  このボタンはそのためにあります。
* **アプリ内の Web コンテンツ**（WebView やサーバー側でレンダリングされた画面）は通常、
  アプリごとのロケールではなく、アカウント設定や `Accept-Language` に従います。
* **一部の OEM ビルドでは強制停止が拒否される場合があります。**ロケール自体は書き込まれるので、
  アプリを手動で閉じれば適用できます。この場合は UI にその旨が表示されます。
* **仕事用プロファイル / セカンダリユーザー：**本アプリは、インストールされたユーザー領域
  （プロファイル）の設定のみを変更します。プロファイルをまたいだ一括適用には対応していません。
* 大幅に変更された OEM ROM では、shell 権限がさらに制限されている場合があります。その場合、
  アプリは黙って失敗せず、`SecurityException` を表示します。

---

## 互換性

すべての処理は標準の AOSP インターフェース、すなわち `"locale"` と `"activity"` の
システムサービス、`PackageManager`、Shizuku を通して行います。OEM ごとの分岐や
端末モデルの許可リストはありません。そのため、AOSP、Pixel、One UI、ColorOS、OriginOS、
HyperOS などで動作することが期待できます。ベンダーが何らかの操作（特に強制停止）を制限している
場合、端末固有の回避策は使わず、エラーをそのまま表示します。

---

## 検証

特権レイヤーの動作は、推測ではなく実際の Android 環境で検証しています。
`app/src/debug/.../LocaleGatewayProbe.kt` は
実際の `LocaleGateway` と `ProcessGateway` クラスを `app_process` により uid 2000 で
実行し、端末上の `LocaleManagerService` に直接接続します。Shizuku もモックも使いません。

```
./gradlew assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/probe.apk
adb shell CLASSPATH=/data/local/tmp/probe.apk app_process /system/bin \
    --nice-name=locale-probe dev.takeru.perapplocale.probe.LocaleGatewayProbe com.android.settings
```

リフレクション経路と raw トランザクション経路の両方を実行し、すべての結果を
`cmd locale get-app-locales` と照合します。API 37 エミュレーターでは 9 項目すべてに合格し、
ハードコードしたトランザクション ID、Parcel のレイアウト、`fromDelegate` 引数が正しいことを
確認しました。

同じエミュレーター上で Shizuku 13.6.0 を実際に起動したエンドツーエンド検証も行っています。
状態遷移（未インストール → 権限が必要 → 準備完了）、権限ダイアログ、サードパーティ製アプリへの
`zh-CN` の適用、強制停止と再起動、システムのデフォルトへのリセットを確認しました。
各段階で UI の表示だけを信用せず、`cmd locale get-app-locales` で結果を確認しています。

ただし、検証環境で利用できた Android バージョンは API 37 だけです。そのため、API 33 の
3 引数版 `setApplicationLocales` 分岐は AOSP ソースを基に実装しており、実機では未実行です。
実行されたのは API 34 以降の 4 引数版です。

固定の OEM 横断手順と、Pixel/AOSP、One UI、ColorOS、HyperOS の現在の検証状況は
[`docs/OEM_SMOKE_TEST.md`](docs/OEM_SMOKE_TEST.md) で管理しています。

---

## アーキテクチャ

```
app/src/main/java/dev/takeru/perapplocale/
├── PerAppLocaleApp.kt          Application。hidden API 制限を解除し、ShizukuRepository を所有
├── MainActivity.kt             単一 Activity、Compose のホスト、イベントの橋渡し
├── core/
│   ├── SystemBinder.kt         サービス検索 + ShizukuBinderWrapper
│   ├── LocaleGateway.kt        get/setApplicationLocales — リフレクション + raw トランザクション
│   └── ProcessGateway.kt       IActivityManager 経由の forceStopPackage
├── shizuku/
│   ├── ShizukuState.kt         READY / PERMISSION_REQUIRED / NOT_RUNNING / NOT_INSTALLED
│   └── ShizukuRepository.kt    Binder + 権限リスナーを StateFlow として公開
├── data/
│   ├── AppInfo.kt              一覧の 1 行分
│   ├── AppRepository.kt        PackageManager クエリ
│   ├── LocaleOption.kt         プリセット + BCP 47 検証
│   └── SettingsStore.kt        DataStore：設定 + ロケール割り当てのローカルミラー
├── ui/                         Compose（Material 3）：MainScreen、LocaleSheet、
│   │                           ShizukuStatusCard、MainViewModel、および DocScreen から構築した
│   │                           2 つの説明画面 SetupScreen と HelpScreen
│   └── theme/Theme.kt          利用可能ならダイナミックカラー。ライト + ダーク
└── util/AppIcon.kt             遅延読み込み、LRU キャッシュ付きのランチャーアイコン
```

データフローは単方向です。`MainViewModel` が Shizuku の状態、パッケージ一覧、DataStore の
設定を 1 つの `MainUiState` にまとめ、UI はその純粋関数として動作します。Snackbar や
アプリ起動などの一度限りの処理は、別のイベントチャンネルを通ります。

ロケールの正しい状態はシステムを情報源とします。DataStore はミラーを保持するだけで、
パッケージごとの Binder スキャンが終わる前でも、設定済みアプリを一覧へ即座に表示するために使います。

---

## ビルド

```
git clone https://github.com/TakeruF/android-perapp-language-selector.git
cd android-perapp-language-selector
./gradlew assembleDebug
```

JDK 17 と Android SDK 36 が必要です。Android Studio で開けば、そのままインポートできます。

### リリースビルド

プロジェクトのルートに `keystore.properties` があれば、`assembleRelease` は APK に署名します。
なくてもビルドは成功しますが、生成される APK は未署名です。

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

このファイルとキーストアはいずれも gitignore の対象です。

---

## 謝辞

* **[VegaBobo/Language-Selector](https://github.com/VegaBobo/Language-Selector)** —
  Shizuku によるアプリごとのロケール制御が可能であることを示した先行プロジェクトです。
  全体的なアプローチの参考にしましたが、本プロジェクトは AOSP ソースと Shizuku API を基にした
  独立実装であり、同プロジェクトのコードは含みません。（将来コードを取り込む場合は、
  Apache-2.0 の帰属表示を正式に追加します。）
* **[RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)** および Shizuku API。
* **[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)**。

## ライセンス

Apache License 2.0 — [LICENSE](LICENSE)を参照してください。
