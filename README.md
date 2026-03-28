# Capella Agent

> AI-powered multi-agent ecosystem for Eclipse Capella, bringing 84 LLM-callable tools for model interaction, architecture analysis, PLM integration, and simulation orchestration directly into the MBSE workbench.

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Eclipse Capella 7.0](https://img.shields.io/badge/Capella-7.0-green.svg)](https://www.eclipse.org/capella/)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL--2.0-orange.svg)](https://www.eclipse.org/legal/epl-2.0/)

## Why This Exists

Systems engineers working in Capella spend significant time on repetitive model queries, cross-tool traceability lookups, manual data exports, and context-switching between simulation tools. Capella Agent places AI assistants directly inside the Capella workbench so you can ask questions about your model in natural language, run architecture analysis, export reports, automate ARCADIA layer transitions, import PLM data from Teamcenter, and run what-if simulations -- all without leaving Eclipse.

## Architecture

```
+----------------------------------------------------------------------+
|  Eclipse Capella 7.0 Workbench                                       |
|                                                                      |
|  +-------------------+  +-------------------+  +------------------+  |
|  |  Model Chat View  |  |  Simulation View  |  |  (Teamcenter)   |  |
|  |  (modelchat.ui)   |  |  (simulation.ui)  |  |  (headless)     |  |
|  +--------+----------+  +--------+----------+  +--------+---------+  |
|           |                      |                       |           |
|  +--------v----------+  +-------v-----------+  +--------v---------+  |
|  |  Model Chat Agent |  |  Simulation Agent |  |  Teamcenter Agent|  |
|  |  71 tools         |  |  6 tools          |  |  7 tools         |  |
|  |  (modelchat)      |  |  (simulation)     |  |  (teamcenter)    |  |
|  +--------+----------+  +--------+----------+  +--------+---------+  |
|           |                      |                       |           |
|  +--------v----------------------v-----------------------v---------+ |
|  |                    Core Platform (core + core.ui)               | |
|  |  LLM Providers  |  Tool Framework  |  Event Bus  |  Security   | |
|  |  (10 providers: |  (registry,      |  (pub/sub   |  (access    | |
|  |   Claude, OpenAI|   descriptors)   |   events)   |   + audit)  | |
|  |   Groq, etc.)  |                   |             |             | |
|  +---------+-------------------+-------------------+---------------+ |
|            |                   |                   |                  |
|  +---------v-----------+       |                   |                  |
|  |  MCP Server Bridge  |       |                   |                  |
|  |  (localhost:9847)    |       |                   |                  |
|  +---------+-----------+       |                   |                  |
|            |                   |                   |                  |
+----------------------------------------------------------------------+
             |        |          |                   |
    +--------v------+ | +-------v--------+   +------v-----------+
    |  LLM APIs     | | |  Capella EMF   |   |  External Systems|
    |  (HTTP/JSON)  | | |  Model + Sirius|   |  (TC REST, MATLAB|
    +---------------+ | +----------------+   +------------------+
              +-------v--------+
              |  Claude Code   |
              |  (MCP stdio)   |
              +----------------+
```

## Tool Categories

Capella Agent ships with **84 tools** across **9 categories**. For the complete tool catalog with descriptions and access modes, see [TOOLS.md](TOOLS.md).

| Category | Tools | Description |
|----------|------:|-------------|
| **Read** | 23 | Query model elements, hierarchies, traceability, interfaces, scenarios, state machines, and more |
| **Write** | 21 | Create, update, delete, move, and clone model elements; batch operations |
| **Diagram** | 9 | Create, update, clone, delete, export, and auto-layout Sirius diagrams |
| **Analysis** | 12 | Validation, cycle detection, impact analysis, complexity metrics, safety reports |
| **Export** | 8 | CSV, JSON, traceability matrices, ICD reports, model reports, diff reports |
| **Transition** | 5 | Automate ARCADIA layer transitions (OA-to-SA, SA-to-LA, LA-to-PA, PA-to-EPBS) and reconciliation |
| **AI-Assisted** | 5 | Architecture review, interface suggestions, auto-allocation, test scenario generation, Q&A |
| **Teamcenter** | 7 | Search, import, and trace Siemens Teamcenter PLM artifacts |
| **Simulation** | 6 | MATLAB/Simulink parameter extraction, execution, result propagation, what-if analysis |

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17+, Eclipse Capella 7.0, OSGi/Equinox |
| Modeling | EMF, Sirius, Capella metamodel (Arcadia) |
| LLM communication | `java.net.http.HttpClient`, Gson (10 providers) |
| MCP integration | Model Context Protocol (JSON-RPC 2.0 over stdio) |
| Build | Maven 3.9+ with Eclipse Tycho |
| Security | Eclipse Equinox Secure Storage |
| Simulation | MATLAB Engine API for Java |
| PLM | Teamcenter Active Workspace REST API |

## LLM Providers (10)

Capella Agent ships with ten LLM providers. Seven extend the `OpenAiCompatibleProvider` abstract base class, making it possible to add a new OpenAI-compatible provider in roughly 15 lines of code.

| Provider | Provider ID | Notes |
|---|---|---|
| Anthropic Claude | `anthropic` | Default provider; recommended for full 84-tool support |
| OpenAI | `openai` | GPT-4o and other models |
| GitHub Models | `github` | Free with GitHub PAT; higher token limits than Groq (endpoint: `https://models.inference.ai.dev/chat/completions`) |
| Groq | `groq` | Fast inference (see token limits note in [SITECONFIGURATIONS.md](SITECONFIGURATIONS.md)) |
| DeepSeek | `deepseek` | DeepSeek models |
| Mistral | `mistral` | Mistral AI models |
| OpenRouter | `openrouter` | Multi-model marketplace |
| Google Gemini | `gemini` | Gemini models |
| Ollama | `ollama` | Local / air-gapped inference |
| Custom Endpoint | `custom` | Any OpenAI-compatible API |

## Quick Start

### 1. Build

```bash
git clone https://github.com/your-org/capella-agent.git
cd capella-agent
mvn clean verify -f pom.xml
```

A successful build produces a **p2 update site** at `com.capellaagent.site/target/repository/`.

### 2. Install

1. In Capella, go to **Help > Install New Software...**.
2. Add the local site: `com.capellaagent.site/target/repository/`.
3. Select **Capella Agent - AI Multi-Agent Ecosystem** and finish the wizard.
4. Restart Capella.

### 3. Configure an LLM Provider

Open **Window > Preferences > Capella Agent > LLM Provider**, select a provider, and enter your API key.

### 4. Ask Your First Question

1. Open a Capella model (`.aird` file).
2. Go to **Window > Show View > Capella Agent > AI Model Chat**.
3. Type: `List all system functions`

### 5. Use with Claude Code (Optional)

1. Ensure Capella is running with a model open (the MCP endpoint starts automatically on port 9847).
2. Open a terminal in the `capella-agent` directory (where `.mcp.json` lives).
3. Run `claude` to start a session. Claude Code discovers the MCP tools automatically.
4. Ask: `List all system functions` -- the query is routed through MCP to the live Capella model.

For complete setup instructions, see [SITECONFIGURATIONS.md](SITECONFIGURATIONS.md).

## Project Structure

```
capella-agent/
|-- com.capellaagent.core/           Core platform: LLM providers, tool framework,
|   |                                event bus, configuration, security, audit
|   +-- src/com/capellaagent/core/
|       |-- config/                  AgentConfiguration (preferences + secure storage)
|       |-- llm/                     ILlmProvider, LlmProviderRegistry, message types
|       |   +-- providers/           ClaudeProvider, OpenAiProvider, OllamaProvider,
|       |                            OpenAiCompatibleProvider (abstract base),
|       |                            GitHubModelsProvider, GroqProvider, DeepSeekProvider,
|       |                            MistralProvider, OpenRouterProvider, GeminiProvider,
|       |                            CustomEndpointProvider
|       |-- tools/                   AbstractCapellaTool, ToolRegistry, IToolDescriptor
|       |-- bus/                     AgentMessageBus, event types
|       |-- security/                AccessMode, SecurityService, AuditLogger
|       |-- session/                 ConversationSession
|       +-- util/                    CapellaSessionUtil, WorkspaceUtil, JsonUtil
|
|-- com.capellaagent.core.ui/        Reusable UI: ChatComposite, MarkdownRenderer
|
|-- com.capellaagent.modelchat/      Model Chat agent: 71 tools for Capella models
|   +-- src/.../modelchat/
|       |-- tools/read/              23 read-only query tools
|       |-- tools/write/             21 model mutation tools
|       |-- tools/diagram/           9 diagram manipulation tools
|       |-- tools/analysis/          12 architecture analysis tools
|       |-- tools/export_/           8 export and reporting tools
|       |-- tools/transition/        5 ARCADIA layer transition tools
|       +-- tools/ai/               5 AI-assisted engineering tools
|
|-- com.capellaagent.modelchat.ui/   Model Chat view: Eclipse view, commands, adapters
|
|-- com.capellaagent.teamcenter/     Teamcenter agent: 7 PLM integration tools
|   +-- src/.../teamcenter/
|       |-- client/                  TcRestClient, TcSession, TcConfiguration
|       |-- api/                     TcSearchService, TcObjectService, TcBomService
|       |-- import_/                 TcToCapellaMapper, RequirementImporter, PartImporter
|       +-- tools/                   7 LLM-callable tools
|
|-- com.capellaagent.simulation/     Simulation agent: 6 orchestration tools
|   +-- src/.../simulation/
|       |-- bridge/                  ISimulationEngine, MatlabEngineBridge, MatlabCommandBridge
|       |-- orchestrator/            SimulationOrchestrator, WhatIfManager, param/result mapping
|       +-- tools/                   6 LLM-callable tools
|
|-- com.capellaagent.simulation.ui/  Simulation dashboard view
|
|-- com.capellaagent.mcp/            MCP Server bridge: Claude Code integration
|   +-- src/.../mcp/
|       |-- McpActivator             Bundle lifecycle; starts HTTP endpoint
|       |-- McpHttpEndpoint          HTTP server on localhost:9847
|       |-- McpToolAdapter           MCP-to-internal tool translation
|       |-- McpServerBridge          Bridge subprocess management
|       +-- McpStdioTransport        JSON-RPC 2.0 stdio framing
|
|-- com.capellaagent.feature/        Eclipse feature definition (groups all plugins)
+-- com.capellaagent.site/           P2 update site for installation
```

**189 Java source files across 10 projects (8 plugins + feature + site).**

## Configuration Summary

| Setting | Location | Default |
|---|---|---|
| LLM provider | Preferences or `CAPELLA_AGENT_LLM_PROVIDER` | `anthropic` |
| API key | Preferences (Secure Storage) or `CAPELLA_AGENT_LLM_API_KEY` | *(none)* |
| Model name | Preferences or `CAPELLA_AGENT_LLM_MODEL` | `claude-sonnet-4-20250514` |
| Temperature | Preferences or `CAPELLA_AGENT_LLM_TEMPERATURE` | `0.3` |
| Max tokens | Preferences or `CAPELLA_AGENT_LLM_MAX_TOKENS` | `4096` |
| Access mode | Preferences or `CAPELLA_AGENT_ACCESS_MODE` | `READ_ONLY` |
| Audit logging | Preferences or `CAPELLA_AGENT_AUDIT_ENABLED` | `true` |
| TC gateway URL | Teamcenter preferences | `http://localhost:7001/tc` |
| MATLAB path | Simulation preferences or `MATLAB_HOME` | *(none)* |
| Simulation timeout | Simulation preferences | `300` seconds |

For the complete configuration reference, see [SITECONFIGURATIONS.md](SITECONFIGURATIONS.md).

## Documentation

| Document | Description |
|---|---|
| [README.md](README.md) | This file -- project overview and quick start |
| [TOOLS.md](TOOLS.md) | Complete catalog of all 84 tools with descriptions and access modes |
| [SITECONFIGURATIONS.md](SITECONFIGURATIONS.md) | Full setup, configuration, and troubleshooting guide |
| [COO.md](COO.md) | Code ownership, architecture, and strategy guide |

## Contributing

Contributions are welcome. Before you start:

1. **Open an issue** describing the change you want to make.
2. **Fork** the repository and create a feature branch from `main`.
3. **Follow the existing code conventions**: Java 17 language features, Javadoc on all public APIs, structured audit logging for new tools.
4. **Add or update documentation** in this README and SITECONFIGURATIONS.md for any user-facing changes.
5. **Write tools by extending `AbstractCapellaTool`** and registering them in the appropriate `ToolRegistrar`.
6. **Run the full build** (`mvn clean verify`) and ensure it passes before submitting a pull request.
7. Submit a **pull request** against `main` with a clear description of the change and its motivation.

### Adding a New Tool

1. Create a class extending `AbstractCapellaTool` in the appropriate tools package.
2. Implement `getName()`, `getDescription()`, `getParametersSchema()`, and `doExecute(JsonObject)`.
3. Register the tool in the relevant `ToolRegistrar` (`ModelChatToolRegistrar`, `TcToolRegistrar`, or `SimToolRegistrar`).
4. Write tools use `requireWriteMode()` to enforce access control. Read tools work in both modes.
5. Update the tool catalog in [TOOLS.md](TOOLS.md).

### Adding a New LLM Provider

1. Implement `ILlmProvider` with `getId()`, `getDisplayName()`, and `chat(...)`.
2. Register the provider in `LlmProviderRegistry` during bundle activation.
3. Document the provider in SITECONFIGURATIONS.md.

## License

This project is licensed under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).

```
Copyright (c) 2024 CapellaAgent Contributors.
All rights reserved. This program and the accompanying materials
are made available under the terms of the Eclipse Public License v2.0
which accompanies this distribution.
```
