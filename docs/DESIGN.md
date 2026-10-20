# kotonote 設計検討

付箋紙のように使える（ようにしかつかえない）メモアプリ。
Android 先行、iOS を視野に入れる。環境は kotodo（`D:\playgrounds\Develop\koto\kotodo`）に合わせる。

> ステータス: 検討段階。「仮決め」は私が置いたデフォルト。覆してよい。
> 「要確認」は実装前にあなたに決めてほしい点（末尾にまとめ）。

---

## 1. コンセプトと「やらないこと」

| やる | やらない（意図的な制約） |
|---|---|
| 5 枚の付箋タブ。1 タブ = 1 枚 | 6 枚目以上 |
| 起動 = 最後に開いていた付箋がそのまま開く | 一覧画面、ファイル名、フォルダ、検索 |
| 1 タップでクリア（確認あり） | ゴミ箱、Undo、履歴、バージョン |
| 再起動・強制終了でも消えない（DB 保存） | クラウド同期、バックアップ、エクスポートファイル |
| 残したいときは共有で他アプリへ（Google Keep 等） | 「メモ帳として保存」機能 |

「残す必要があるなら他のアプリを使う」が思想なので、**共有（Share）がこのアプリ唯一の出口**。
共有の品質（テキスト + 画像/動画/音声を 1 回で渡せること）がアプリの価値の半分になる。

## 2. 画面

```
┌──────────────────────────┐
│ kotonote     ●1 ○2 ○3 ○4 ○5 │ ← ヘッダー（タイトル + 5枚のタブ）+ 共有アイコン
├──────────────────────────┤
│                          │
│   一面一色（タブごとの色）  │ ← 入力欄（全面）
│   テキスト…               │
│   🎤 🖼 🎬 ✏  ← 添付はアイコン │
│                          │
│ [🗑]               [＋]   │
└──────────────────────────┘
  左下: クリア         右下: 展開 → 🎤 録音 / 📷 撮影(画像・動画) / 🖼 選択 / ✏ ペイント
```

- **ヘッダー**: ヘッダー色だけは固定（kotodo の鉄紺系）。ヘッダー内に 5 タブ。
  タブは番号 + その付箋の色の丸。中身がある付箋は丸が塗り、空は輪郭のみ（開かなくても使用状況が分かる）。
  共有アイコンもヘッダー右端に置く（アクションボタンは「入れる」「消す」だけに保つため）。
- **本体**: ヘッダー以外は選択中タブの色で 1 色ベタ。タブ切替で全面の色が変わる。
  仮決めの色: 黄 `#FFF59D` / ピンク `#F8BBD0` / 水色 `#B3E5FC` / 緑 `#C5E1A5` / 橙 `#FFCC80`。
  ダークモードは彩度を落とした暗色版（kotodo の `values-night` 方式）。文字色は固定の濃色/淡色。
- **右下 FAB**: タップで展開（ミニ FAB が上方向に並ぶ）。録音 / カメラ / ギャラリー / ペイント。
  カメラは「写真」「動画」を選ぶ必要があるので、カメラ FAB 押下で小さなメニュー（写真・動画）を出す。
- **左下 FAB**: クリア。確認ダイアログ「この付箋を消しますか？」→ 消す/やめる。**今のタブだけ**消す。
- **添付アイコン**: 種類のみ表示（🎤 音声 / 🖼 画像 / 🎬 動画 / ✏ ペイント）。サムネイルは出さない（コンセプト通り）。
  - 画像 / ペイント: タップで全画面ビューア。
  - 動画: タップで全画面プレイヤー。
  - 音声: タップで再生/停止（その場でアイコンが ▶/■ に変わる）。
  - ペイント: タップで **再編集可能なペイント画面**を開く（仮決め。他は表示のみだが、ペイントは「描き足したい」が自然）。
  - 長押し: その添付だけ削除（確認なし、またはスナックバーで確認。要確認）。

## 3. データモデル

**構造化できる単位を最初から決めておく**のが iOS 対応で最も効く。Android 固有の概念をモデルに持ち込まない。

### 3.1 論理モデル（プラットフォーム非依存）

```
Note   : 固定 5 件。slot = 0..4（作成・削除はされず、中身が空になるだけ）
Block  : Note の中身を並べたもの。position 順。
         type = TEXT | AUDIO | IMAGE | VIDEO | PAINT
         TEXT  → text
         他    → file（Note 専用ディレクトリ内の相対パス）, mime, durationMs(音声/動画のみ)
```

テキストと添付の「並び」を持つ（テキスト → 画像 → テキスト…）。これは付箋を「ただの 1 つの文章」ではなく
貼り付けた物の集まりとして使うために必要で、後から足すのが最も面倒な部分なので最初から入れる。

### 3.2 SQLite（Room、kotodo と同じ）

```
notes(slot INTEGER PRIMARY KEY CHECK(slot BETWEEN 0 AND 4), updatedAt INTEGER NOT NULL)
blocks(id INTEGER PRIMARY KEY AUTOINCREMENT,
       slot INTEGER NOT NULL REFERENCES notes(slot) ON DELETE CASCADE,
       position INTEGER NOT NULL,
       type TEXT NOT NULL,
       text TEXT,            -- TEXT のみ
       filePath TEXT,        -- 添付のみ。相対パス
       mime TEXT, durationMs INTEGER,
       createdAt INTEGER NOT NULL)
settings(key TEXT PRIMARY KEY, value TEXT)   -- 最後に開いたタブ等。SharedPreferences で十分なら不要
```

- `notes` は初回起動時に 5 行を INSERT（`onCreate` コールバック）。以後 INSERT/DELETE しない。
  → 「5 枚しか使えない」をアプリコードではなく**スキーマで保証**する（iOS 側でも同じ制約をそのまま移植できる）。
- 日時は kotodo と同様に epoch 値。DB 名 `kotonote.db`、`exportSchema = false`、マイグレーションは kotodo 方式。

### 3.3 ファイル保存

- `filesDir/notes/<slot>/<uuid>.<ext>` に**コピーして保持**（アプリ専用領域）。
  ギャラリーから選んだ画像/動画も参照ではなくコピー: 元が消えても付箋は壊れない。
  代償は容量。大きな動画を選ぶとストレージを食う → 上限を設ける（要確認）。
- DB には相対パスのみ（端末移行・パス変更に強く、iOS のコンテナパス変更にも同じ理由で効く）。
- クリア = DB の blocks を削除 + `notes/<slot>/` ディレクトリを削除。**ファイル削除を先、DB を後**にしない
  （クラッシュ時に参照切れが残るより孤児ファイルが残る方がまし）→ DB を先に消し、起動時に孤児ファイルを掃除。
- `allowBackup="false"`（kotodo と同じ。クラウドへ漏らさない方針）。`INTERNET` 権限は宣言しない。

## 4. 保存のタイミング（「消えない」の担保）

- テキスト: 入力から約 500ms のデバウンスで書き込み + `onPause`/`onStop` で必ず同期書き込み。
  プロセスが突然死しても失うのは直近 0.5 秒だけ。
- 添付: 取り込み（コピー）完了 → DB 追加 の順。途中でも付箋が壊れた状態にならない。
- タブ切替時、現在のタブを先に保存してから切り替える。
- 録音中にアプリがバックグラウンドへ行ったら**停止して保存**（フォアグラウンドサービスは使わない。複雑さに見合わない）。
- Room の書き込みはバックグラウンドスレッド（kotodo の Repository パターンに合わせる）。

## 5. 機能ごとの実装方針（Android）

| 機能 | 方式 | 権限 |
|---|---|---|
| 録音 | `MediaRecorder`（AAC / `.m4a`）。録音中は経過時間表示 + 停止ボタン | `RECORD_AUDIO`（実行時） |
| 写真撮影 | `ActivityResultContracts.TakePicture` + FileProvider | なし（`CAMERA` を宣言しなければ不要） |
| 動画撮影 | `ActivityResultContracts.CaptureVideo` + FileProvider | 同上 |
| 既存画像/動画 | `ActivityResultContracts.PickVisualMedia`（Photo Picker） | なし |
| ペイント | 自前 `View`（Canvas、ペン色・太さ・消しゴム・全消し・Undo）。PNG で保存 | なし |
| 画像表示 | `ImageView`（ズームは後回し） | なし |
| 動画再生 | `VideoView` + `MediaController`、全画面 Activity | なし |
| 音声再生 | `MediaPlayer` | なし |
| 共有 | `ACTION_SEND_MULTIPLE`、テキスト + `content://`（FileProvider）の画像/動画/音声 | なし |

カメラ/ピッカー系は intent 経由なので**権限を最小化できる**（権限が少ない = 信頼性のアピールになる。kotodo の思想と一致）。

### 共有の詳細

- 共有対象は「今開いている付箋」。ヘッダーの共有アイコンから。
- テキストブロックは結合して `EXTRA_TEXT`、添付は `EXTRA_STREAM`。
  Google Keep は text + 画像を受けるが、**動画・音声を受けない受信側は多い**ので、保証できるのは text / 画像まで。
  「全部渡す」ではなく「渡せる物は渡す」に留め、受信側の都合は仕様とする。
- 付箋が空なら共有アイコンは無効化。

## 6. パッケージ構成（kotodo に合わせる）

```
com.mugime.kotonote
├─ KotonoteApp / MainActivity
├─ data/        KotonoteDatabase, NoteDao, BlockDao, NoteRepository, Converters
├─ elements/    Block, BlockType, Note       ← Android import を持たない純 Java（iOS 移植時の「仕様」）
├─ files/       AttachmentStore（コピー/削除/孤児掃除）, ShareHelper
├─ media/       AudioRecorderController, AudioPlayerController
├─ ui/
│   ├─ note/    NoteFragment（ブロック一覧）, NoteViewModel, BlockAdapter
│   ├─ paint/   PaintActivity, PaintView
│   └─ viewer/  ImageViewerActivity, VideoPlayerActivity
└─ utils/
```

- **画面は単一 Activity + 5 タブ**（`TabLayout`）。タブごとに Fragment を作らず、`NoteViewModel` が選択中 slot を持つ
  シンプルな構成（`ViewPager2` のスワイプ切替は入れない: 描画ジェスチャ・テキスト選択と競合する）。
- 本文は `RecyclerView` + ブロック種別ごとの ViewHolder（TEXT = `EditText`、他 = アイコン行）。
  代替案は「1 つの `EditText` に `ImageSpan` でアイコンを埋め込む」方式。見た目は付箋らしく連続するが、
  カーソル・Undo・IME・スパン永続化が難しく壊れやすい。→ **ブロック方式を推奨**。
  隣接する TEXT ブロックはタップした位置で分割/結合して、テキストの連続感を保つ。

## 7. ビルド環境（kotodo 踏襲）

| | kotodo | kotonote |
|---|---|---|
| 言語 | Java | Java |
| Gradle / AGP | Kotlin DSL / 8.13.2 | 同じ（`libs.versions.toml` を流用） |
| minSdk / target / compile | 26 / 36 / 36 | 同じ |
| UI | AppCompat + Material + ViewBinding | 同じ（Compose は使わない） |
| 永続化 | Room 2.6.1（annotationProcessor） | 同じ |
| applicationId | `com.mugime.kotodo` | `com.mugime.kotonote` |
| 表示言語 | en + ja（`values-ja`、localeConfig） | 同じ |
| CI/リリース | `.github/workflows/ci.yml` / `release.yml`、タグで署名 APK を draft release | 流用（シークレット名を `KOTONOTE_*` に） |
| 署名 | `keystore.properties`（gitignore） | 同じ。キーストアは**別物を作る**（kotodo と共有しない） |

追加依存は最小限: `androidx.activity`（Result API。既に推移的に入る）, `androidx.core`（FileProvider）。外部ライブラリは入れない。

## 8. iOS を視野に入れた準備

Android を Java（Views）で作る以上、**コード共有はできない**。現実的な戦略は「仕様を共有する」:

1. **ドメインを純粋にする**: `elements/` に Android 依存を入れない。ブロック種別・5 枚固定・クリア規則・共有規則を、
   この `DESIGN.md` と `elements/` の 2 か所で言語非依存に記述しておく。
2. **相対パス + 種別 + mime** という保存形式（3.3）はそのまま iOS に移せる。
3. 対応表（将来の SwiftUI 版）:

| Android | iOS |
|---|---|
| Room / SQLite | SwiftData または Core Data（スキーマは 3.2 と同形） |
| `MediaRecorder` / `MediaPlayer` | `AVAudioRecorder` / `AVAudioPlayer` |
| `TakePicture` / `CaptureVideo` | `UIImagePickerController`（または `AVCaptureSession`） |
| Photo Picker | `PHPickerViewController` |
| 自前 Paint `View` | `PencilKit`（`PKCanvasView`）— ほぼ無料で高品質 |
| `ACTION_SEND_MULTIPLE` | `UIActivityViewController` |
| `INTERNET` 非宣言 | iOS には相当する仕組みなし（「通信コードを入れない」ことで担保） |

4. 他の選択肢との比較: Kotlin Multiplatform / Flutter / Tauri mobile なら UI も共有できるが、
   kotodo と「同じ環境」でなくなり、録音・カメラ・ペイント・共有はいずれもネイティブ連携が主役なので得が薄い。
   → **Android 先行 + 仕様共有 + iOS は別実装**を推奨。iOS のビルドには Mac が必要（この Windows 環境では不可）。

## 9. 想定外・注意点

- **容量**: コピー保持なので動画で膨らむ。上限（1 付箋あたりの添付数 or 合計サイズ）が必要。クリアで必ず解放される点を UI で伝える。
- **プライバシー**: ペイント/録音/撮影が含まれるため、アプリのスクリーンショット許可やタスク切替サムネイルの扱いを決める
  （仮決め: 制限しない。`FLAG_SECURE` は付けない）。
- **クリアの誤爆**: 確認ダイアログは必須（要件通り）。Undo は設けない（コンセプトに反する）。
- **共有先でのデータ寿命**: `content://` URI は一時権限。共有後にクリアしても共有先が取り込み済みなら問題ない。
- **5 枚固定とタブ名**: タブは名前を付けさせない（番号 + 色のみ）。名前を付けさせると「メモ帳化」する。
- **アプリ終了 ≠ 消去**: 要件通り、スワイプで終了しても残る。ただし端末の「アプリのデータ消去」では全て失われる（仕様）。
- **アクセシビリティ**: 色だけに頼らず、タブには番号を付ける。FAB・アイコンに `contentDescription`。

## 10. マイルストーン案

1. **M1 骨格**: プロジェクト雛形（kotodo 複製 → リネーム）、5 タブ、テキスト入力、Room 保存、クリア、再起動復元。
2. **M2 画像**: ギャラリー選択・撮影 → コピー保持 → アイコン表示 → ビューア。共有（text + 画像）。
3. **M3 音声/動画**: 録音・再生、動画撮影/選択・再生。
4. **M4 ペイント**: Paint 画面、再編集。
5. **M5 仕上げ**: ダークモード、ja/en、アイコン、README、CI/リリース、孤児掃除、容量上限。

## 11. 決定事項

| # | 論点 | 決定 |
|---|---|---|
| 3 | 上限 | 添付は 1 付箋あたり 10 個まで / 動画は 1 分まで / 音声は 5 分まで（`NoteRules`） |
| 4 | 個別削除 | 専用 UI は作らない。添付アイコンは本文中にインラインで並び、**キーボードの削除でアイコンが消えたら添付も削除**する |
| 5 | 言語 | Java（kotodo 踏襲）。Kotlin も可だが、現状は Java で統一 |
| 6 | リポジトリ | 新規リポジトリ。kotodo の CI / リリース構成・LICENSE（MIT）を流用 |
| - | 公開 | **GitHub public** で公開予定 → 鍵・パスワード・`local.properties`・個人情報を一切コミットしない（`.gitignore` 済み）。キーストアは kotodo と別に作る |

### 本文の構造（決定 4 を受けた変更）

「アイコンをキーボードで消す」は、添付が文章と同じ流れの中にあることを意味する。
そこで **1 つの `EditText` に添付アイコンを `ImageSpan` として埋め込む**方式を採用する（旧案のブロック一覧方式は不採用）。

- 保存形式（DB の `blocks`）は変えない。保存時に EditText を走査し、テキスト区間 → TEXT ブロック、`ImageSpan` → 添付ブロックに分解する。
  読み込み時は逆に、ブロックを並べて 1 つの `Editable` に組み立てる。UI 方式が変わっても DB と iOS 移植の仕様は影響を受けない。
- アイコンの実体は U+FFFC（オブジェクト置換文字）+ `ImageSpan` の 1 文字。1 文字なのでカーソル移動・Backspace・Delete で自然に 1 個消える。
- 保存時、DB にあって EditText から消えた添付は **ファイルも削除**する（孤児掃除と兼用）。
- `ImageSpan` を伴わない U+FFFC（他アプリからの貼り付け等）は入力時に除去する。
- 添付アイコンのタップは `ClickableSpan` 相当（`LinkMovementMethod` を使わず `onTouch` で位置からスパンを引く）で処理。
- ペイントのタップは再編集（決定 2 は推奨どおり）。

## 12. 進捗

- [x] M1 骨格: 5 タブ（色付き）、テキスト入力、Room 保存（500ms デバウンス + onStop）、クリア（確認あり）、最後のタブ復元、en/ja、ダーク配色
- [x] M2 画像（撮影・ギャラリー）/ アイコン埋め込み / ビューア / キーボード削除で添付削除 / 共有（共有はビルドのみ、実機未確認）
- [x] M3 音声・動画（録音5分上限・動画1分上限・再生。ギャラリーの動画選択は未確認）
- [x] M4 ペイント（ペン色6・太さ3・消しゴム・元に戻す・全消去、タップで再編集、PNG保存）
- [x] M5 仕上げ（README、CI/リリース構成、共有・ギャラリー動画・1分超拒否・ダークモードをエミュレータで確認）
