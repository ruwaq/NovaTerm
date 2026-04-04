# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.2.x   | Yes       |
| < 0.2   | No        |

## Reporting a Vulnerability

**Do NOT open a public issue for security vulnerabilities.**

Instead, please report security issues via:

1. **GitHub Security Advisories**: [Report a vulnerability](https://github.com/PrometeoDEV/NovaTerm/security/advisories/new)
2. **Email**: Create a private advisory on GitHub (preferred)

### What to include

- Description of the vulnerability
- Steps to reproduce
- Impact assessment
- Suggested fix (if any)

### Response timeline

- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 1 week
- **Fix**: Depends on severity (critical: ASAP, high: 1 week, medium: 2 weeks)

## Security Model

### Terminal Process Isolation

- Each terminal session runs in its own process (forked via PTY)
- Sessions are isolated from each other at the OS level
- The app runs as a regular Android app (no root required)

### MCP Server Security

- Localhost-only binding (127.0.0.1)
- SecurityPolicy blocks dangerous commands (`rm -rf /`, `dd`, etc.)
- ApprovalManager gates tool execution
- Disabled by default — user must opt-in via Settings

### On-Device LLM

- Models downloaded from HuggingFace only
- Inference runs entirely on-device (no data leaves the phone)
- LLM is optional — disabled by default
- No telemetry, no analytics, no cloud calls

### Data Storage

- Session metadata: app-private JSON (not accessible to other apps)
- Block store: app-private SQLite with WAL mode
- Preferences: app-private SharedPreferences
- Bootstrap: extracted to app-private directory
- No data is sent to any server

### What We Don't Do

- No telemetry or analytics
- No crash reporting to external services
- No network calls except user-initiated (apt, SSH, model download)
- No access to contacts, camera, microphone, or location
- No ads, no tracking
