# Capella Agent

**Capella Agent** is an AI-powered multi-agent ecosystem embedded inside Eclipse Capella.
It lets systems engineers query, explore, and modify their ARCADIA models using plain English,
driven by state-of-the-art large language models.

## Key Capabilities

| Capability | Details |
|---|---|
| **95+ model tools** | Read, write, diagram, analysis, export, transition, AI intelligence, Teamcenter, simulation |
| **10 LLM providers** | Claude (Anthropic), OpenAI, Azure OpenAI, GitHub Models, Groq, Mistral, OpenRouter, Ollama, LM Studio, Custom OpenAI-compatible |
| **MCP bridge** | All 95+ tools exposed via the Model Context Protocol so Claude Code in your terminal can access your live Capella model |
| **4 agent modes** | General Assistant, Sustainment Engineer, Requirements Analyst, Architect |
| **Offline / air-gapped** | Use Ollama or LM Studio for fully local, ITAR-safe operation |
| **Hardcoded safety baselines** | Token spend, iteration depth, and tool-result size are bounded by fixed guardrails — protecting against runaway loops and cost surprises |

## Quick Start

1. Install the plugin using `reinstall.bat` or Install New Software.
2. Configure an LLM provider — **GitHub Models is free** and requires no billing setup.
3. Open the IFE sample model and send your first query.

Full step-by-step documentation is available inside Capella at
**Help → Help Contents → Capella Agent**.

## Providers

Supports 10 providers including free-tier options:

- **GitHub Models** (free, GPT-4o and more, GitHub token required)
- **Groq** (free tier, extremely fast inference hardware)
- **Ollama / LM Studio** (fully local, no data leaves your machine — approved for ITAR)

## Security

Capella Agent was designed from the ground up for use with sensitive engineering data:

- API keys stored in Eclipse Secure Preferences (OS-level encryption)
- Write-operation consent dialog before any model change — **Deny is the default**
- Prompt-injection defence on all tool results
- Content-Security-Policy on the chat browser widget
- Path validation (workspace containment + symlink rejection) on all file imports
- Per-session and per-day token budgets to prevent runaway spend
- Rolling HMAC audit log of every tool call

See the [Security & Governance](reference/security.md) page for full details.

## License

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
