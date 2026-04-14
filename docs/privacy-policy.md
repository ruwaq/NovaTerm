# NovaTerm Privacy Policy

**Effective Date:** April 5, 2026
**Last Updated:** April 5, 2026
**App Name:** NovaTerm - AI Terminal
**Package:** com.nvterm
**Developer:** NovaTerm Contributors (nvterm)

---

## Summary

NovaTerm does not collect, transmit, or share any personal data. The app runs entirely on your device with no telemetry, analytics, or advertising.

---

## 1. Data Collection

**NovaTerm collects no data.** Specifically:

- No telemetry or usage analytics
- No crash reporting or diagnostics sent to any server
- No advertising or ad tracking
- No device fingerprinting
- No user accounts or login required
- No cookies or web tracking

## 2. Network Access

NovaTerm makes **no network calls** on its own. Network access occurs only when you explicitly initiate it through commands you type in the terminal, such as:

- **Package management:** `apt`, `pkg`, or similar commands that download packages from Termux-compatible repositories
- **SSH connections:** Outbound SSH sessions you initiate
- **AI tool API calls:** If you install and use AI coding tools (Claude Code, Gemini CLI, Aider, OpenCode), those tools communicate with their respective API endpoints using API keys you provide. NovaTerm itself does not make these calls; the tools running inside the terminal do.
- **Any other command** that uses the network (curl, wget, git, etc.)

NovaTerm does not intercept, log, or inspect your network traffic.

## 3. Local Data Storage

The following data is stored locally on your device in app-private storage (accessible only to NovaTerm):

- **Terminal sessions:** Session metadata (ID, shell path, working directory, title, timestamp) stored as JSON in app-private storage
- **Command history:** Managed by your shell (bash/zsh) in standard history files within the app's private directory
- **Session content:** Terminal output blocks stored in a local SQLite database (BlockStore) with content-addressable deduplication
- **User preferences:** Theme, font size, color scheme, and other settings stored via Android DataStore
- **API keys:** If you configure API keys for AI tools, they are stored using Android EncryptedSharedPreferences (AES-256-GCM encryption backed by the Android Keystore hardware security module). Keys never leave your device through NovaTerm.
- **Bootstrap files:** The terminal environment (shell, coreutils, and other packages) extracted from assets bundled with the APK

All local data is deleted when you uninstall the app, following standard Android behavior.

## 4. AI and MCP Server

NovaTerm includes an optional MCP (Model Context Protocol) server for AI tool integration:

- The MCP server binds exclusively to **localhost** (127.0.0.1) and is not accessible from the network
- MCP authentication uses a per-session bearer token stored in app-private storage
- Dangerous commands are blocked by a built-in SecurityPolicy
- An AutoApprovalManager denies dangerous tool invocations
- Rate limiting is enforced (60 requests per minute)

The planned on-device LLM feature (command prediction) runs entirely on your phone's NPU/CPU. No data is sent to external servers for this feature.

## 5. Permissions

NovaTerm requests only the following Android permissions:

- **FOREGROUND_SERVICE:** To keep terminal sessions alive in the background
- **RECEIVE_BOOT_COMPLETED:** To optionally restore sessions after device restart (user-configurable)
- **RECORD_AUDIO:** For optional voice input (speech-to-text processed by Android's on-device SpeechRecognizer; audio is not stored or transmitted by NovaTerm)
- **INTERNET:** Required for user-initiated network commands (SSH, package downloads, etc.)

NovaTerm does not request access to contacts, location, camera, phone, SMS, or any other sensitive permissions.

## 6. Third-Party Services

NovaTerm does not integrate any third-party SDKs for analytics, advertising, or tracking. The app contains no Google Analytics, Firebase Analytics, Facebook SDK, or similar services.

Bootstrap packages are downloaded from Termux-compatible package repositories. These downloads are initiated by you and follow the same privacy practices as the Termux package ecosystem.

## 7. Children's Privacy

NovaTerm does not knowingly collect any information from anyone, including children under the age of 13. Since the app collects no data at all, it is compliant with COPPA and similar regulations.

## 8. Data Sharing

NovaTerm shares no data with third parties because it collects no data. There is nothing to share.

## 9. Security

- API keys are encrypted at rest using AES-256-GCM via Android Keystore
- Session data is stored in app-private storage protected by Android's sandboxing
- The MCP server is localhost-only with token authentication
- Deep link command execution (nvterm://run) is disabled to prevent command injection
- Sensitive data is redacted from debug logs

## 10. Open Source

NovaTerm is open source under the Apache License 2.0. You can audit the source code at any time:

- **Source code:** [https://github.com/novaterm-org/NovaTerm](https://github.com/novaterm-org/NovaTerm)
- **Terminal core:** Derived from Termux (Apache License 2.0)

## 11. Changes to This Policy

If we update this privacy policy, the changes will be posted to this page with an updated "Last Updated" date. Since NovaTerm collects no data, changes would likely only reflect new features or clarifications.

## 12. Contact

If you have questions or concerns about this privacy policy:

- **GitHub Issues:** [https://github.com/novaterm-org/NovaTerm/issues](https://github.com/novaterm-org/NovaTerm/issues)
- **Discussions:** [https://github.com/novaterm-org/NovaTerm/discussions](https://github.com/novaterm-org/NovaTerm/discussions)

---

*This privacy policy applies to NovaTerm version 0.3.0-alpha and later.*
