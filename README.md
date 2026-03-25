# Capella Agent

> AI-powered multi-agent ecosystem for Eclipse Capella, bringing LLM-driven model interaction, Teamcenter PLM integration, and simulation orchestration directly into the MBSE workbench.

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Eclipse Capella 7.0](https://img.shields.io/badge/Capella-7.0-green.svg)](https://www.eclipse.org/capella/)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL--2.0-orange.svg)](https://www.eclipse.org/legal/epl-2.0/)

## Why This Exists

Systems engineers working in Capella spend significant time on repetitive model queries, cross-tool traceability lookups, and manual simulation workflows. Capella Agent places AI assistants directly inside the Capella workbench so you can ask questions about your model in natural language, import PLM data from Teamcenter without leaving Capella, and run what-if simulations guided by an LLM that understands your architecture.

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
|  |  16 tools         |  |  6 tools          |  |  7 tools         |  |
|  |  (modelchat)      |  |  (simulation)     |  |  (teamcenter)    |  |
|  +--------+----------+  +--------+----------+  +--------+---------+  |
|           |                      |                       |           |
|  +--------v----------------------v-----------------------v---------+ |
|  |                    Core Platform (core + core.ui)               | |
|  |  LLM Providers  |  Tool Framework  |  Event Bus  |  Security   | |
|  |  (9 providers:  |  (registry,      |  (pub/sub   |  (access    | |
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

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17+, Eclipse Capella 7.0, OSGi/Equinox |
| Modeling | EMF, Sirius, Capella metamodel (Arcadia) |
| LLM communication | `java.net.http.HttpClient`, Gson (9 providers) |
| MCP integration | Model Context Protocol (JSON-RPC 2.0 over stdio) |
| Build | Maven 3.9+ with Eclipse Tycho |
| Security | Eclipse Equinox Secure Storage |
| Simulation | MATLAB Engine API for Java |
| PLM | Teamcenter Active Workspace REST API |

## Features

### Model Chat Agent (16 tools)

Query, modify, and visualize your Capella model through natural language.

**Read tools** -- work in both `READ_ONLY` and `READ_WRITE` modes:

| Tool | Description |
|---|---|
| `ListElements` | List model elements by layer and type |
| `GetElementDetails` | Retrieve full details of a specific element |
| `SearchElements` | Search elements by name, type, or keyword |
| `GetHierarchy` | Navigate containment and decomposition trees |
| `ListDiagrams` | List all diagrams in the model |
| `ListRequirements` | List requirements and their allocations |
| `GetTraceability` | Follow traceability links across layers |
| `ValidateModel` | Run model validation rules and report issues |

**Write tools** -- require `READ_WRITE` mode:

| Tool | Description |
|---|---|
| `CreateElement` | Create a new model element (component, function, etc.) |
| `CreateExchange` | Create functional or component exchanges |
| `AllocateFunction` | Allocate functions to components |
| `CreateCapability` | Create operational or system capabilities |
| `UpdateElement` | Modify properties of an existing element |
| `DeleteElement` | Remove an element from the model |

**Diagram tools**:

| Tool | Description |
|---|---|
| `UpdateDiagram` | Add or remove elements on a diagram |
| `RefreshDiagram` | Refresh a diagram to reflect model changes |

### Teamcenter Agent (7 tools)

Bridge Capella models with Siemens Teamcenter PLM data.

| Tool | Description |
|---|---|
| `TcSearch` | Full-text search across Teamcenter objects |
| `TcGetObject` | Retrieve detailed object properties |
| `TcGetBom` | Navigate bill-of-materials structures |
| `TcListRequirements` | List requirements from Teamcenter |
| `TcImportRequirement` | Import a Teamcenter requirement into Capella |
| `TcImportPart` | Import a Teamcenter part as a Capella component |
| `TcLink` | Create traceability links between Capella and TC objects |

### Simulation Agent (6 tools)

Orchestrate simulation engines and propagate results back into the model.

| Tool | Description |
|---|---|
| `ListEngines` | List available simulation engines (MATLAB, etc.) |
| `ExtractParams` | Extract simulation parameters from model elements |
| `RunSimulation` | Execute a simulation on a connected engine |
| `PropagateResults` | Write simulation outputs back to model properties |
| `WhatIf` | Run what-if analysis with parameter variations |
| `GetSimStatus` | Check the status of a running or completed simulation |

### MCP Server Bridge

The `com.capellaagent.mcp` plugin exposes all 29 tools to Claude Code via the Model Context Protocol. An embedded HTTP endpoint on `localhost:9847` communicates with a bridge subprocess that speaks MCP stdio (JSON-RPC 2.0), so you can query and modify your Capella model from the Claude Code terminal.

### LLM Providers (9)

Capella Agent ships with nine LLM providers. Six extend the `OpenAiCompatibleProvider` abstract base class, making it possible to add a new OpenAI-compatible provider in roughly 15 lines of code.

| Provider | Provider ID | Notes |
|---|---|---|
| Anthropic Claude | `anthropic` | Default provider |
| OpenAI | `openai` | GPT-4o and other models |
| Groq | `groq` | Fast inference |
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
|       |                            GroqProvider, DeepSeekProvider, MistralProvider,
|       |                            OpenRouterProvider, GeminiProvider, CustomEndpointProvider
|       |-- tools/                   AbstractCapellaTool, ToolRegistry, IToolDescriptor
|       |-- bus/                     AgentMessageBus, event types
|       |-- security/                AccessMode, SecurityService, AuditLogger
|       |-- session/                 ConversationSession
|       +-- util/                    CapellaSessionUtil, WorkspaceUtil, JsonUtil
|
|-- com.capellaagent.core.ui/        Reusable UI: ChatComposite, MarkdownRenderer
|
|-- com.capellaagent.modelchat/      Model Chat agent: 16 tools for Capella models
|   +-- src/.../modelchat/
|       |-- tools/read/              8 read-only query tools
|       |-- tools/write/             6 model mutation tools
|       +-- tools/diagram/           2 diagram manipulation tools
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

**114 Java source files, 16,210 lines of code across 10 projects (8 plugins + feature + site).**

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
| [SITECONFIGURATIONS.md](SITECONFIGURATIONS.md) | Full setup, configuration, and troubleshooting guide |
| [COO.md](COO.md) | Code ownership and contribution areas |

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
5. Update the tool reference table in this README.

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
