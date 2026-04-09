# Security & Governance

This page describes the security protections built into Capella Agent v1.0.
For the full threat model, dependency inventory, and vulnerability reporting instructions,
see [`SECURITY.md`](https://github.com/digitalthreadai/capella-agent/blob/main/SECURITY.md)
in the repository root.

## Credentials

All API keys are stored in **Eclipse Secure Preferences** (`ISecurePreferences`) using
OS-level encrypted storage — never in plain-text `.prefs` files or in memory longer than
necessary.

- Keys are transmitted only in outbound HTTPS requests to the provider — never logged,
  never included in error messages shown to users.
- The Preferences page **never pre-populates** the key field after the first save. An empty
  field means a key is stored; leaving it blank and clicking Apply keeps the existing key
  unchanged.
- The Gemini provider sends the API key in the `x-goog-api-key` request header — not as a
  URL query parameter — so the key does not appear in proxy logs or browser history.

!!! tip
    If the key field is blank after reopening Preferences, click **Test Connection** to
    confirm the stored key still works before entering a new one.

## Write-Operation Consent

No tool that modifies your Capella model runs without your explicit approval. Every
write-tool call triggers a native SWT **WriteConsentDialog** that blocks execution until
you respond.

| Dialog element | Detail |
|---|---|
| Default button | **Deny** — pressing Enter without moving the cursor denies the action |
| Close with X | Treated as Deny |
| LLM reasoning section | Rendered as plain text, labelled "untrusted", capped at 300 characters. Cannot contain HTML or control characters. |
| "Remember my choice" checkbox | Available for ordinary MODEL_WRITE tools only; session-scoped (reset on restart) |
| Destructive tools | Delete, merge, apply architecture diff — always prompt; "Remember" checkbox disabled |

!!! warning
    **The LLM reasoning text is untrusted.** A requirement or diagram label containing a
    prompt-injection payload cannot manufacture consent by making the reasoning look
    official. Capella Agent strips HTML, newlines, and caps the text before display.
    Evaluate the *tool name and arguments*, not the reasoning, when deciding to approve.

## Cost & Rate Controls

These limits are **fixed safety baselines** in v1.0. They protect against runaway
tool-calling loops, compromised sessions, and provider outages — a budget that a user can
disable with one click protects nothing.

| Guardrail | Default | Why this number |
|---|---|---|
| Per-session token budget | 1,000,000 tokens | Caps a runaway loop at roughly single-digit dollars per session on Claude Opus |
| Per-day hard cap (UTC reset) | 10,000,000 tokens | Caps a compromised or forgotten session at low-hundreds of dollars per day |
| Provider circuit breaker | 5 errors in 120 s → open 5 min, exponential backoff | Calibrated against real-world provider outage patterns; prevents silent retry storms |
| Iteration hard ceiling | 50 tool calls per turn | Absolute backstop regardless of agent mode |
| Per-tool-result byte cap | 200 KB | Prevents a single huge result from consuming the entire budget in one call |

**Administrator override:** An administrator who needs a different daily cap can call
`DailyTokenLimiter.getInstance().setCap(long tokens)` from a Capella startup hook or OSGi
console. This is an admin path — it is deliberately not wired into the Preferences UI so
that end users cannot silently disable the guardrail.

## Prompt-Injection Defence

Every string returned by a tool — element names, requirement text, diagram labels — is
processed by `ToolResultSanitizer` before being fed back to the LLM:

- Wrapped in `<untrusted>...</untrusted>` markers so the LLM knows the text originated
  from the model, not from a trusted instruction.
- Scanned for 14 injection patterns: fake system tags (`[SYSTEM]`, `### SYSTEM`),
  role-prefix lines, "ignore previous instructions" variants, homoglyph substitutions.
- Zero-width and bidirectional control characters are stripped.
- Text is NFKC-normalised before pattern matching to defeat Unicode substitutions.

If a suspicious pattern is detected, a `suspiciousContextFound` flag is set for the
remainder of the turn. Any write-tool call in that turn is forced through the consent
dialog regardless of session memoization.

## Path Safety

All tool parameters that accept file paths are validated by `PathValidator` before any
file operation:

- Path is canonicalised and checked against the Eclipse workspace root.
- Symlinks and reparse points are rejected (`NOFOLLOW_LINKS`).
- Extension whitelist is enforced: `.reqif` / `.xml` for ReqIF import, `.xlsx` / `.xlsm`
  for Excel import, `.png` / `.svg` / `.jpg` for diagram export.
- `.reqifz` archives are disabled pending a ZipSlip-safe extractor.
- Violation messages do not include the rejected path (prevents information disclosure).

## XSS Hardening in the Chat View

The chat browser widget runs under a strict **Content Security Policy**:
`default-src 'none'` with only the minimum directives needed for the bridge script and
inline styles. Additionally:

- All agent-produced HTML passes through `HtmlSanitizer` before rendering — only a tag
  whitelist is allowed; all `on*` event handlers, `javascript:` URLs, and `data:` URLs
  are stripped.
- UUIDs are validated against a strict regex before being embedded in element links.
- Architecture proposal names and requirement text are HTML-escaped before insertion into
  diff widgets and coverage dashboards.

## Audit Log

Every tool call — approved, denied, or blocked — is written to
`{workspace}/.capella-agent/audit.log`.

| Property | Detail |
|---|---|
| Location | `{Eclipse workspace}/.capella-agent/audit.log` |
| Rotation | 10 MB per file, up to 10 files (`audit.log` through `audit.log.9`) |
| Integrity | Rolling HMAC-SHA256 chain — each line carries a `_hmac` field chained to the previous line. Detects casual tampering. |
| Secret scrubbing | Fields named `api_key`, `token`, `password`, `secret`, `credential`, `authorization`, and `Authorization: Bearer` patterns are replaced with `***` before writing |

!!! note "HMAC limitation"
    The rolling HMAC detects *accidental/casual* tampering, not a determined attacker with
    JVM filesystem access (who can read the HMAC key from Eclipse secure preferences and
    re-sign). This limitation is documented in `SECURITY.md` as an accepted risk.

!!! tip "ITAR / air-gapped environments"
    When using Ollama or LM Studio, no data leaves your machine. The audit log remains
    entirely local. This is the approved path for ITAR-controlled programme models.

## Further Reading

- [`SECURITY.md`](https://github.com/digitalthreadai/capella-agent/blob/main/SECURITY.md) — full threat model, dependency inventory (with CVE closure notes), accepted risks, and vulnerability reporting instructions
- Known Limitations #12–14 (in-product Help) — consent dialog latency, audit log retention policy, token budget admin API
