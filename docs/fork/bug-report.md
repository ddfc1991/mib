# One-tap bug report (fork design)

Status: **design + round-1 stub**. Round 1 ships a Settings › Feedback ›
"Report a bug" row that opens a pre-filled GitHub issue on
`dl1991/OpenMinis` (app version, Android/API, device, ROM, ABIs), falling back
to the system share sheet. Everything below the "Round 2" line is not built yet.

## Goals

- A user who hits a bug can send a useful report in under 30 seconds.
- Nothing leaves the device without the user seeing exactly what is sent.
- Secrets never leave the device, even if the user does not read the preview.

## What is collected

| Item | Source | Default |
|---|---|---|
| App version, build type, flavor, versionCode | `BuildConfig` | on |
| Android version / API level, device, ROM (`Build.DISPLAY`, `Build.FINGERPRINT` brand part), ABIs | `android.os.Build` | on |
| Sandbox state: rootfs flavor, proot loader present, Shizuku state | existing diagnostics | on |
| Recent app log (last ~2,000 lines / 15 min) | `AppLogger` files under `filesDir/logs/` | on |
| Last crash report, if newer than the previous report | ACRA files in `filesDir/logs/` | on if present |
| Screenshot of the screen the user came from | captured before the report sheet opens (`PixelCopy` on the window) | off, opt-in toggle |
| Free-text description | user | required |

Never collected: chat contents, memory files, prompts, provider configs,
environment variables, mounted-folder paths beyond their count.

## Redaction (before preview, applied again before upload)

A single `LogRedactor` pass, unit-tested against fixtures:

- API keys / tokens: `sk-…`, `sk-ant-…`, `ghp_…`, `github_pat_…`, `xox[abp]-…`,
  `AIza…`, JWTs (`eyJ…\.…\.…`), `Bearer …`, `Authorization:` header values,
  generic `(?i)(api[_-]?key|token|secret|password)\s*[:=]\s*\S+`.
- URLs: keep scheme + host, drop query strings and userinfo.
- E-mail addresses, phone numbers, IPv4/IPv6 (keep `127.0.0.1`/`::1`).
- Values of every env var and vault entry the app knows about (exact-match
  replacement, so custom secret formats are covered too).
- File paths under `/storage/emulated/0/` collapse to `<external>/…`.
- Each replacement becomes `‹redacted:kind›`; the preview shows a count per kind.

## Flow

1. Entry points: Settings › Feedback › Report a bug; long-press the app's crash
   notification; the post-crash "share report" dialog that already exists.
2. Sheet: description field, toggles (logs / crash / screenshot), and a
   **Preview** button that renders the exact Markdown body + attachment list.
3. Send options:
   - **GitHub issue (direct)**: if the user configured a fine-grained token
     with `Issues: write` on the fork (stored in the Keystore-backed vault from
     feat/vault-cli; never shown to the model), POST `/repos/{repo}/issues`.
     Logs are attached as a collapsed `<details>` block (truncated to 60 KB) or,
     when larger, as a secret gist linked from the issue. Screenshot is uploaded
     to the issue via a gist (GitHub's REST API has no issue-attachment upload).
   - **Browser**: open `issues/new?title=…&body=…` (URL-length limited to
     ~7 KB, so logs are summarised and the full log is put on the clipboard).
   - **Share sheet**: zip of `report.md`, `log.txt`, `screenshot.png` via
     `FileProvider`, for e-mail/Telegram.
4. After sending, show the issue URL and keep a local copy under
   `filesDir/bug-reports/` for 7 days (deletable from Settings › Logs).

## Configuration

- Target repo defaults to `dl1991/OpenMinis` (`ForkBugReport.REPO`), overridable
  in Settings for other forks.
- Token is optional; without it only Browser / Share are offered.
- Rate limit: one direct upload per 2 minutes.

## Testing

- JVM tests for `LogRedactor` (fixtures with every secret kind; assert zero
  survivors) and for the Markdown body builder (length caps, escaping).
- Robolectric test that the preview text equals the uploaded body byte-for-byte.
- CI smoke test opens the sheet and screenshots the preview.

## Round 2 work items

- [ ] `LogRedactor` + tests
- [ ] Report sheet UI with preview
- [ ] Screenshot capture (opt-in)
- [ ] GitHub direct upload with vault token; gist for large logs/screenshots
- [ ] Share-sheet zip fallback via FileProvider
- [ ] Crash-notification entry point
