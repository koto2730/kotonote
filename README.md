# kotonote

A sticky note for Android that is only ever a sticky note.
付箋紙のように使える（ようにしかつかえない）メモアプリ。

kotonote has five notes in five tabs, and that is all. No list screen, no file names,
no folders, no search, no history. Everything is kept in a local SQLite database, so it
survives restarts. If something is worth keeping, share it to another app (Google Keep,
Drive, Messages…) and let that app keep it.

kotonote は付箋 5 枚（タブ 5 つ）だけのアプリです。一覧画面もファイル名もフォルダも検索も履歴もありません。
内容は端末内の SQLite に保存されるので、再起動しても消えません。残したいものは共有で他のアプリ
（Google Keep など）へ。

kotonote does not declare `android.permission.INTERNET`, so it is technically incapable of
sending your data anywhere.

kotonote は `android.permission.INTERNET` を宣言していないため、そもそも通信できません。

## Features / 機能

- **Five notes** — one colour each (yellow, pink, blue, green, orange); the whole page below
  the header is the note. A tab's dot is filled when its note has something on it.
  The app reopens on the note you left.
- **Text and attachments in one flow** — attachments sit inside the text as small icons, so
  you can write around them. Press delete/backspace on an icon and the attachment is removed.
- **Voice** — record up to 5 minutes; tap the icon to play, tap again to stop.
- **Pictures** — take a photo or pick one. It shows as a thumbnail in the text; tap it to view it
  full screen. Long-press a thumbnail to make it small, medium or large.
- **Share into kotonote** — share a picture or video (a screenshot, say) from any app and choose
  which of the five notes gets it.
- **Video** — record or pick up to 1 minute; it shows its first frame, tap to play it.
- **Drawing** — a finger-paint sheet (6 colours, 3 pen sizes, eraser, undo, clear); tap the
  thumbnail later to keep drawing on it.
- **Up to 10 attachments per note.**
- **One-tap clear** — bottom-left button, with a confirmation. It clears the current note
  and deletes its files.
- **Share** — the header's share button sends the text and attachments to another app.
  What the receiving app accepts is up to that app: most take text and pictures, fewer take
  audio or video.
- **Light and dark**, **English and Japanese** (follows the device language).

## What it deliberately does not do / あえてやらないこと

No sixth note, no list, no folders, no save-as-file, no cloud sync, no backup, no undo for
"clear". Cloud backup is excluded, so notes are never uploaded to your Google account, and the
app itself never transmits anything. The one exception is the system's own device-to-device
transfer when you move to a new phone (Android 12+, depending on your device's settings): the
notes are included in it, so they can come with you. On Android 11 and older they are not carried over.

## Requirements / 動作環境

| | |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 36 |
| Persistence | SQLite via AndroidX Room |
| UI | AppCompat + Material + ViewBinding |
| Build | Gradle + Android Gradle Plugin 8.13 |
| Permissions | `RECORD_AUDIO` only (asked when you first record) |

The camera and the photo picker are used through system intents, so kotonote needs no camera
or storage permission.

## Build / ビルド

Requires JDK 17+ (21 recommended) and the Android SDK (`local.properties` with `sdk.dir`,
which is gitignored).

```bash
./gradlew assembleDebug
```

Unit tests and lint:

```bash
./gradlew testDebugUnitTest lintDebug
```

## CI/CD and releases / CI/CDとリリース

Every push to `main` and every PR runs unit tests, lint and a debug build via
[`.github/workflows/ci.yml`](.github/workflows/ci.yml). It needs no secrets.

Pushing a tag like `v1.0.0` builds a **signed** release APK (for sideloading) and a signed AAB
(for Google Play) and attaches both to a **draft** GitHub Release via [`.github/workflows/release.yml`](.github/workflows/release.yml). The
release is a draft on purpose: nothing is public until you check the files and click
**Publish release**.

### Signing / 署名

Create a keystore **of your own** for kotonote (do not reuse another app's). Signing
material is never committed: `*.keystore`, `*.jks` and `keystore.properties` are gitignored.

Locally, copy [`keystore.properties.example`](keystore.properties.example) to
`keystore.properties`, point it at your keystore, and run `./gradlew assembleRelease`.
Without it, `assembleRelease` still runs but produces an unsigned APK.

For CI, set four repository secrets from your own terminal:

```bash
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
base64 -w0 /path/to/your/release.keystore | gh secret set KEYSTORE_BASE64
```

(PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release.keystore")) | gh secret set KEYSTORE_BASE64`)

Then bump `versionCode` / `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts) and:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Data model / データモデル

Two tables. `notes` always holds exactly five rows (slots 0–4), created with the database and
never inserted or deleted afterwards — "five notes only" is enforced by the data, not just the
UI. `blocks` holds each note's content in reading order: a run of `TEXT`, or an attachment
(`AUDIO`, `IMAGE`, `VIDEO`, `PAINT`) that points at a file by relative path.

Attachment files are copied into the app's private storage (`files/notes/<slot>/`), so a note
survives its source being deleted from the gallery. Saves are debounced (about 0.5 s) and always
flushed when the app leaves the foreground. Files that no note refers to are swept at start-up.

Design notes and decisions (Japanese): [docs/DESIGN.md](docs/DESIGN.md)
Privacy policy: [PRIVACY.md](PRIVACY.md) · Play Store listing text and assets: [store/](store/listing.md)

## iOS

Not built yet. The storage format (relative paths, block types, five fixed slots) and the
product rules in `elements/NoteRules` are platform-neutral on purpose, so a native iOS version
can follow the same spec; see the mapping in [docs/DESIGN.md](docs/DESIGN.md).

## License

MIT — see [LICENSE](LICENSE).
