# Support — Capella Agent

Thank you for using Capella Agent. This document explains how to get help, report bugs, request features, and find documentation.

## Quick Help

| I want to... | Do this |
|---|---|
| **Install the plugin** | Read [`SITECONFIGURATIONS.md`](SITECONFIGURATIONS.md) |
| **Understand what it can do** | Read [`docs/TOOLS.md`](docs/TOOLS.md) — full catalog of 116 tools with example prompts |
| **Configure an LLM provider** | Open `Window → Preferences → Capella Agent` in Eclipse |
| **Try the AI Model Chat** | Open the view via `Window → Show View → Other → Capella Agent → AI Model Chat` |
| **Use Capella Agent from Claude Code** | See the MCP bridge section in [`SITECONFIGURATIONS.md`](SITECONFIGURATIONS.md) |

## Reporting Bugs

Open an issue on GitHub:

- **Repository:** https://github.com/digitalthreadai/capella-agent
- **Issues:** https://github.com/digitalthreadai/capella-agent/issues/new

### Before opening a bug, please include:

1. **What happened** — exactly what you typed or clicked, and what the agent did
2. **What you expected** — what the correct behavior would have been
3. **Capella Agent version** — visible in `Help → About → Installation Details`
4. **Eclipse Capella version** — e.g. `7.0.1`
5. **Java version** — `java -version` output
6. **OS** — e.g. `Windows 11`, `macOS 14.4`, `Ubuntu 22.04`
7. **LLM provider** — e.g. `Claude (Anthropic)`, `Groq`, `Ollama (Local)`, `GitHub Models (Free)`
8. **Model name** — e.g. `claude-sonnet-4-20250514`, `llama-3.3-70b-versatile`
9. **Reproduction steps** — minimum steps to trigger the bug
10. **Logs** — error log entries from `Window → Show View → Other → General → Error Log`
    - Filter by plugin: type `com.capellaagent` in the filter

If the chat showed a friendly error message, include the **first line of that message** verbatim. Do **not** redact the error category emoji (⏳, 📝, ⚠, 🔑, 🌐) — they help triage.

### Privacy when reporting

Do not include:
- API keys or personal access tokens
- Customer model file contents (.aird, .melodymodeller)
- Any text from your model that contains confidential information

If a bug requires a model to reproduce, please use one of the public Capella sample models (IFE, Cancer Research, etc.) or create a minimal anonymized model.

## Requesting Features

Feature requests are welcome. Open an issue with the `enhancement` label or start a discussion on GitHub Discussions.

Before requesting a new tool, check:
- [`docs/TOOLS.md`](docs/TOOLS.md) — the tool may already exist under a different name
- [`docs/STRATEGIC_PLAN.md`](docs/STRATEGIC_PLAN.md) — it may be on the roadmap

## Asking Questions

For general questions about how to use the plugin:

- **GitHub Discussions** (preferred): https://github.com/digitalthreadai/capella-agent/discussions
- **Eclipse Capella Forum** (community): https://forum.mbse-capella.org/

When asking on the Capella forum, please tag the post with `capella-agent` so it's findable by other users.

## Security Issues

**Do not open a public issue for security vulnerabilities.**

If you discover a security issue (especially related to LLM prompt injection, model write tools, or credential handling), please report it privately:

- **Email:** security@digitalthreadai.com (or open a private GitHub Security Advisory)
- **Response time:** within 5 business days for acknowledgment

We will work with you on responsible disclosure and credit you in the changelog if you wish.

## Documentation

| Document | What it is |
|---|---|
| [`README.md`](README.md) | Project overview, architecture, quick start |
| [`SITECONFIGURATIONS.md`](SITECONFIGURATIONS.md) | Build, install, configure providers, set up MCP bridge |
| [`COO.md`](COO.md) | Strategy and feature guide |
| [`docs/TOOLS.md`](docs/TOOLS.md) | Full catalog of 116 tools with example prompts |
| [`docs/STRATEGIC_PLAN.md`](docs/STRATEGIC_PLAN.md) | Long-term product roadmap |
| [`docs/PRODUCTION_PLAN_WEEKS_1-6.md`](docs/PRODUCTION_PLAN_WEEKS_1-6.md) | Current 6-week production hardening plan |
| [`docs/index.html`](docs/index.html) | Interactive HTML version of all docs (open in browser) |

## Compatibility

| Component | Supported version |
|---|---|
| Eclipse Capella | **7.0.x** (tested on 7.0.1) |
| Java Runtime | **17 or newer** (Capella ships with JDK 17) |
| OS | Windows 10/11, macOS 12+, Linux (any glibc-2.31+) |

The plugin **may** work on Capella 6.x but is not officially supported. If you need 6.x support, open an issue and we'll evaluate.

## Response Times

This is an open-source project maintained on best-effort basis:

- **Bug acknowledgment:** within 7 days
- **Critical bugs** (data loss, security): within 2 days
- **Feature requests:** triaged monthly
- **Pull requests:** reviewed within 14 days

## Contributing

Contributions are welcome. See `CONTRIBUTING.md` (coming soon) for the development setup, code style, and PR process.

## Commercial Support

If you need a guaranteed SLA, dedicated engineering support, custom tool development, on-premises deployment assistance, or training, contact: **commercial@digitalthreadai.com**

## License

Capella Agent is licensed under the Eclipse Public License v2.0. See [`LICENSE`](LICENSE) for the full text.
