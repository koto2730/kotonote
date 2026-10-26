# Google Play ストア掲載メモ / Store listing

Play Console にそのまま貼れる文面と、提出物・手続きのチェックリスト。
素材は `store/` 配下。アイコンとフィーチャー画像は `python store/make_graphics.py` で再生成できる。

## 素材 / Assets

| 項目 | ファイル | 仕様 |
|---|---|---|
| アプリアイコン | `icon-512.png` | 512×512、32bit PNG、角丸は Play が付けるので全面塗り |
| フィーチャーグラフィック（英） | `feature-graphic-en.png` | 1024×500、PNG（透過なし） |
| フィーチャーグラフィック（日） | `feature-graphic-ja.png` | 同上 |
| スマホ スクリーンショット（英） | `screenshots/en/01〜05` | 1080×1920（9:16）、5 枚 |
| スマホ スクリーンショット（日） | `screenshots/ja/01〜05` | 同上 |

スクリーンショットの内容: 01 付箋のテキストと添付アイコン / 02 添付が混在した付箋 / 03 追加メニュー（撮影・選択・動画・録音・お絵かき）/ 04 お絵かき画面 / 05 ダークモード。
Play の要件は 2 枚以上（最大 8 枚）。タブレット用は、タブレット対応をうたわない限り必須ではない。

## 基本情報 / Basics

- パッケージ名: `com.mugime.kotonote`（公開後は変更不可）
- カテゴリ: 仕事効率化（Productivity）
- 既定の言語: 日本語 または English（もう一方は「翻訳を追加」で登録）
- 連絡先メール・（任意）ウェブサイト: https://github.com/koto2730/kotonote
- 価格: 無料、広告なし、アプリ内課金なし

## 日本語 / Japanese

**アプリ名（30文字以内）**

```
kotonote - 付箋メモ
```

**簡単な説明（80文字以内）**

```
テキスト・音声・写真・お絵かきを貼れる、5枚だけの付箋メモ。通信しません。
```

**詳しい説明（4000文字以内）**

```
kotonote は、付箋のようにしか使えないメモアプリです。

付箋は5枚だけ。一覧画面も、ファイル名も、フォルダも、検索もありません。開いたら、さっきの付箋がそのまま開きます。残しておきたいことは、共有で Google Keep などの別のアプリへ。kotonote は「ちょっと書いておく」ことだけに集中します。

■ できること
・5枚の付箋を色分けしたタブで切り替え（黄・ピンク・青・緑・オレンジ）。入力のあるタブは色が塗られます
・テキストと一緒に、音声・写真・動画・お絵かきを貼り付け。写真・動画・お絵かきはサムネイルとして、文章の中に並びます
・サムネイルは長押しで小・中・大に変更
・スクリーンショットなど、他のアプリで共有した画像や動画を、5枚のうち好きな付箋に貼れます
・録音は5分まで、動画は1分まで、添付は1枚につき10個まで
・タップすると再生・表示。お絵かきは続きから描き足せます
・アイコンをキーボードの削除キーで消すと、添付も一緒に削除されます
・ワンタップでクリア（確認ダイアログつき）
・共有ボタンで、文章と添付を他のアプリへ送れます
・ライトモード／ダークモード対応、日本語／英語対応

■ あえてやらないこと
・6枚目以上の付箋、一覧画面、メモ帳としての保存
・クラウド同期やクラウドバックアップ。内容は端末の中だけに保存されます（機種変更の際は、Android の端末間データ移行で引き継げる場合があります）

■ プライバシー
・通信しません。インターネット権限を宣言していないため、データを外へ送ることができません
・広告も解析ツールも入っていません
・マイク権限は、録音ボタンを押したときだけ使います。カメラ・ストレージの権限は不要です

```

## English

**Title (≤ 30 characters)**

```
kotonote - Sticky Notes
```

**Short description (≤ 80 characters)**

```
Five sticky notes for text, voice, photos and drawings. Offline and private.
```

**Full description (≤ 4000 characters)**

```
kotonote is a notes app that can only be used like a sticky note.

You get five notes. No list screen, no file names, no folders, no search. Open the app and the note you left is right there. If something is worth keeping, share it to another app such as Google Keep. kotonote is just for jotting things down.

FEATURES
- Five notes in colour-coded tabs (yellow, pink, blue, green, orange). A tab's dot is filled when its note has something on it
- Put voice recordings, photos, videos and drawings right into the text. Pictures, videos and drawings show as thumbnails in the flow of your writing
- Long-press a thumbnail to make it small, medium or large
- Share a screenshot, picture or video from any app and choose which of the five notes gets it
- Recordings up to 5 minutes, videos up to 1 minute, up to 10 attachments per note
- Tap an attachment to play or view it. Drawings can be reopened and drawn on again
- Press delete on an icon with the keyboard and the attachment is removed too
- Clear a note in one tap (with a confirmation)
- Share the text and attachments to other apps with the share button
- Light and dark themes, English and Japanese

ON PURPOSE, IT DOES NOT
- Offer a sixth note, a list of notes, or "save as a file"
- Sync or back up to the cloud. Your notes stay on your device (when you move to a new phone, Android's own device-to-device transfer can bring them along)

PRIVACY
- It never touches the network. The app does not declare the internet permission, so it cannot send your data anywhere
- No ads and no analytics
- The microphone is used only when you press record. No camera or storage permission is needed

Your notes survive closing and restarting the app.
```

## 提出前チェックリスト / Before you submit

**必須の手続き**

- [ ] Play Console のデベロッパーアカウント（初回 25 USD）。個人アカウントの場合、新規アプリは**クローズドテストで 12 人以上が 14 日間連続でオプトイン**してから本番公開できる（2023 年 11 月以降に作成した個人アカウントの条件）
- [x] **AAB で提出する。** `v*` タグの release workflow が、APK に加えて署名済み AAB（`kotonote-<タグ>.aab`）を draft リリースに添付する。Play にはこの AAB をアップロードする
- [ ] **Play App Signing に登録する。** 今の `kotonote-release.jks` は「アップロード鍵」として使う。Play が配信用の鍵を別に管理する。GitHub Releases で配る APK と Play 配信版は署名鍵が違うため、同じ端末で上書き更新はできない（どちらかを選んで入れる）
- [x] **プライバシーポリシーの URL。** `RECORD_AUDIO`（機密性の高い権限）を使うため必須。`PRIVACY.md`（英日併記）を用意した。Play Console には https://github.com/koto2730/kotonote/blob/main/PRIVACY.md を登録する
- [ ] **データセーフティ**: 収集するデータ「なし」、共有「なし」。通信しない、広告・解析なし、と申告できる
- [ ] **コンテンツのレーティング**（質問票）: 暴力・性的表現などなし。ユーザー生成コンテンツは端末内のみで、他のユーザーとは共有されない
- [ ] 対象年齢、広告の有無（なし）、アプリのアクセス権（ログイン不要）、ニュースアプリではない、などの宣言

**アプリ側で確認しておくとよいこと**

- [ ] 次のタグを打つ前に `versionCode` を上げる（Play は同じ `versionCode` を受け付けない）。今は 1（`v0.0.1`）
- [ ] 実機での確認（エミュレータでは確認済み）。特に録音・撮影、共有先ごとの受け取り可否
- [ ] ストアの説明の「5分」「1分」「10個」が `elements/NoteRules` と一致していること
