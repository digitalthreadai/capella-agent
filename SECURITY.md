# Security — Capella Agent

This document describes the security posture of Capella Agent after the
Week 7-8 Security Hardening Sprint (Phases 1-3 + adversarial N1-N9 findings).

## Threat Model

**Assets**
- Capella `.aird`/`.capella` model data (IP-sensitive architecture work)
- LLM provider API keys (stored in Eclipse secure preferences)
- End-user workstation files (filesystem + process execution)
- Requirements source files (ReqIF/Excel imports — often contain attacker content)

**Attackers considered**
1. A **malicious LLM response** — a compromised or jailbroken model that emits
   prompt injection, XSS payloads, or tool-call sequences designed to exfiltrate
   or destroy data.
2. **Prompt injection via third-party content** — a requirement, issue
   description, or diagram label crafted to hijack subsequent tool calls.
3. **Supply-chain or dependency vulnerabilities** — e.g. POI ZIP-bomb CVEs.
4. **Local filesystem adversaries** — a user with read access to the workspace
   attempting to tamper with audit logs or read encrypted keys.

**Out of scope**
- A determined attacker with arbitrary JVM/filesystem access. Such an attacker
  can read `ISecurePreferences` cleartext, re-sign the rolling-HMAC audit log,
  and tamper with any file. We detect *casual* tampering, not adversarial.
- Multi-user session isolation. Capella Agent assumes a single-workstation
  deployment.
- Network MITM beyond default TLS 1.2+ validation. Certificate pinning was
  considered and deferred.

## Security Posture Summary

| Area | Protection |
|---|---|
| **Credentials** | Eclipse `ISecurePreferences` (encrypted). API keys never in URL query strings (fixed Gemini A1). `AuthToken` wrapper redacts `toString()`. Preferences page no longer pre-populates the key field. |
| **Transport** | Java `HttpClient` defaults (TLS 1.2+). No custom TrustManagers, no disabled hostname verification. |
| **LLM error handling** | `ErrorMessageFilter` maps exceptions to generic user-facing messages; full detail only at `SEVERE`. Response bodies truncated to 500 chars on log. `ProviderErrorExtractor` parses error code/type/status only — never `message`. |
| **Prompt injection** | `ToolResultSanitizer` wraps all tool output strings in `<untrusted>` markers, detects 14 prompt-injection patterns including bracket/markdown-heading role-prefix variants, normalises NFKC, strips zero-width characters. |
| **XSS in chat UI** | `HtmlSanitizer` whitelist (tag allow-list + strip `on*` handlers + block `javascript:`/`vbscript:`/`data:` URLs). Strict UUID regex on element-link generation. CSP `default-src 'none'` + locked-down directives. |
| **Path traversal** | `PathValidator` canonicalises, enforces workspace containment, rejects symlinks (`NOFOLLOW_LINKS`), checks extension whitelist. Applied to ReqIF, Excel, and diagram-export tools. |
| **XML / POI DoS** | XXE disabled (`disallow-doctype-decl`), `FEATURE_SECURE_PROCESSING` enabled. File size caps (100 MB ReqIF, 50 MB Excel). POI `ZipSecureFile` min-inflate-ratio 0.01 + max-entry/text-size set once at bundle start. `.reqifz` rejected pending a ZipSlip-safe extractor. |
| **Tool-level consent** | Native SWT `WriteConsentDialog`. **Deny is the default button.** Closing with X = deny. LLM reasoning is rendered as plain text with an "untrusted" label and 300-char cap. Destructive tools disable the "remember my choice" affordance. |
| **Rate & cost controls** | Per-session token budget (default 1M), per-day hard cap (default 10M UTC), per-provider circuit breaker (5 errors/120s → open 5 min, exponential backoff), iteration hard ceiling 50, per-tool-result byte cap 200K. |
| **Audit log** | `AuditLogger` writes to `{workspace}/.capella-agent/audit.log`. Rolling HMAC-SHA256 chain (key in secure preferences) detects casual tampering. Size-based rotation (10 MB × 10 files). UTF-8-safe argument truncation. JSON secret-key and `Authorization: Bearer` patterns are scrubbed before write. |
| **Staging area** | Full 128-bit UUID diffIds. Project-name check on apply prevents cross-project proposal replay. 10-minute TTL + 60 s background sweep + 100-entry cap. |
| **MATLAB bridge** | `validateModelPath` enforces `.slx`/`.mdl`, workspace containment, no-symlink, and rejects MATLAB script metacharacters (`;`, `%`, single-quote, `system(`, `eval(`, etc.). Every invocation is audited. |

## Dependency Inventory

| JAR | Version | Upstream | Last Reviewed |
|---|---|---|---|
| `poi` | 5.3.0 | Apache POI | 2026-04-08 |
| `poi-ooxml` | 5.3.0 | Apache POI | 2026-04-08 |
| `commons-compress` | 1.27.1 | Apache Commons | 2026-04-08 (closes CVE-2024-25710, CVE-2024-26308) |
| `xmlbeans` | 5.3.0 | Apache XMLBeans | 2026-04-08 |
| `commons-io` | 2.15.1 | Apache Commons | 2026-04-08 |
| `curvesapi` | 1.08 | bundled with POI | 2026-04-08 |
| `commons-math3` | 3.6.1 | Apache Commons | 2026-04-08 |
| `commons-collections4` | 4.4 | Apache Commons | 2026-04-08 |
| `SparseBitSet` | 1.3 | bundled with POI | 2026-04-08 |
| `gson` | (bundled via core) | Google | 2026-04-08 |

A future sprint should add OWASP `dependency-check-maven` to the CI pipeline;
currently reviewed manually per release.

## Known Limitations (LOW-severity items deferred)

These were identified by the security audit and are **accepted risks** for the
current release:

- **No certificate pinning** for LLM providers — TLS validation is on, but
  corporate MITM proxies are tolerated and a compromised CA cannot be detected.
- **No external KMS for audit log signing** — the rolling HMAC key lives in
  Eclipse secure preferences. An attacker with JVM filesystem access can read
  it and re-sign the log. Detection is "casual tampering" only.
- **No API key rotation workflow** — users must rotate keys manually via
  Preferences.
- **Consent prompt rate limit** — bounded only by `maxWriteToolsPerTurn` (5).
  A future improvement could add explicit per-session prompt-count tracking.
- **`.reqifz` archive support** — disabled pending a ZipSlip-safe extractor.
- **Audit log viewer** — no in-product read-only viewer; users must open the
  file manually.
- **FIPS 140 validated crypto** — not a requirement for current users.
- **Workspace-wide encryption at rest** — relies on OS-level disk encryption.

## Reporting Vulnerabilities

Please open a private security advisory through this repository's GitHub
Security tab, or email the maintainers directly before disclosing publicly.
Do not open public issues for suspected security vulnerabilities.

## Security Update Cadence

- **Immediate** for known-exploited CRITICAL findings.
- **Quarterly** for HIGH-severity dependency updates and this document review.
- **Opportunistic** (alongside feature work) for MEDIUM items.

## Public Documentation

The [Security &amp; Governance](https://digitalthreadai.github.io/capella-agent/reference/security/)
page on the public doc site summarises the protections listed above in user-friendly form.
The source for that page is `docs/content/reference/security.md`.

## Verification — Smoke Tests

See `docs/SMOKE_TEST_CHECKLIST.html` Section 32 ("Security Smoke Tests") for
the manual verification checklist covering every protection listed above.
