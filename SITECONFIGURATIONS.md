# Capella Agent -- Setup and Configuration Guide

This guide walks you through building, installing, and configuring the Capella Agent plugin ecosystem inside Eclipse Capella. Follow the sections in order for a first-time setup, or jump to a specific section if you are reconfiguring an existing installation.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build Setup](#2-build-setup)
3. [Installation in Capella](#3-installation-in-capella)
4. [LLM Provider Configuration](#4-llm-provider-configuration)
5. [Teamcenter Configuration](#5-teamcenter-configuration)
6. [Simulation Configuration](#6-simulation-configuration)
7. [Security and Access Control](#7-security-and-access-control)
8. [Claude Code MCP Integration](#8-claude-code-mcp-integration)
9. [Verification](#9-verification)
10. [Setup Checklist](#10-setup-checklist)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

Install the following before you begin.

| Requirement | Version | Purpose |
|---|---|---|
| Eclipse Capella | 7.0+ | Host MBSE workbench |
| Java JDK | 17+ | Build and runtime |
| Apache Maven | 3.9+ with Tycho | Plugin build system |
| MATLAB (optional) | R2022b+ | Simulation agent engine |
| Teamcenter Active Workspace (optional) | -- | PLM integration agent |

Verify your Java and Maven versions from a terminal:

```bash
java -version    # must report 17 or higher
mvn -version     # must report 3.9+ and reference JDK 17+
```

> **Note**: The Capella installation bundles its own JRE. You need a separate JDK 17+ only for building the plugins with Maven/Tycho. At runtime, Capella's embedded JRE is used.

---

## 2. Build Setup

### 2.1 Clone the Repository

```bash
git clone https://github.com/your-org/capella-agent.git
cd capella-agent
```

### 2.2 Configure the Capella Target Platform

Tycho needs to resolve dependencies against the Capella target platform. If your organization hosts a Capella p2 repository, set the `CAPELLA_TARGET_URL` environment variable before building:

```bash
export CAPELLA_TARGET_URL=https://artifacts.company.com/capella/7.0/
```

Alternatively, point Tycho at your local Capella installation by editing the target platform definition in the parent `pom.xml`.

### 2.3 Build All Plugins

Run from the repository root:

```bash
mvn clean verify -f pom.xml
```

A successful build produces:

- Individual plugin JARs in each bundle's `target/` directory.
- A **p2 update site** at `com.capellaagent.site/target/repository/`.

The update site directory contains `artifacts.jar`, `content.jar`, and the plugin/feature JARs that Capella uses for installation.

> **Capella p2 repository**: If the build fails because it cannot resolve Capella dependencies, ensure you have set `CAPELLA_TARGET_URL` to point at a Capella p2 repository (see section 2.2). Alternatively, you can generate a local p2 repo from your Capella installation by pointing the target platform definition at the `plugins/` and `features/` directories inside your Capella install folder.

### 2.4 Build Output Summary

| Artifact | Location |
|---|---|
| Core plugin | `com.capellaagent.core/target/com.capellaagent.core-1.0.0-SNAPSHOT.jar` |
| Core UI plugin | `com.capellaagent.core.ui/target/com.capellaagent.core.ui-1.0.0-SNAPSHOT.jar` |
| Model Chat plugin | `com.capellaagent.modelchat/target/com.capellaagent.modelchat-1.0.0-SNAPSHOT.jar` |
| Model Chat UI plugin | `com.capellaagent.modelchat.ui/target/com.capellaagent.modelchat.ui-1.0.0-SNAPSHOT.jar` |
| Teamcenter plugin | `com.capellaagent.teamcenter/target/com.capellaagent.teamcenter-1.0.0-SNAPSHOT.jar` |
| Simulation plugin | `com.capellaagent.simulation/target/com.capellaagent.simulation-1.0.0-SNAPSHOT.jar` |
| Simulation UI plugin | `com.capellaagent.simulation.ui/target/com.capellaagent.simulation.ui-1.0.0-SNAPSHOT.jar` |
| MCP Server Bridge plugin | `com.capellaagent.mcp/target/com.capellaagent.mcp-1.0.0-SNAPSHOT.jar` |
| Feature | `com.capellaagent.feature/target/com.capellaagent.feature-1.0.0-SNAPSHOT.jar` |
| **Update site** | **`com.capellaagent.site/target/repository/`** |

---

## 3. Installation in Capella

1. Open Capella.
2. Navigate to **Help > Install New Software...**.
3. Click **Add...**, then **Local...** and browse to `com.capellaagent.site/target/repository/`.
4. Give the site a name (for example, `Capella Agent Local`), and click **Add**.
5. Select **Capella Agent - AI Multi-Agent Ecosystem** from the feature list.
6. Click **Next**, accept the license (EPL-2.0), and click **Finish**.
7. Restart Capella when prompted.

After restart, the Capella Agent views and preference pages are available from the **Window** menu.

---

## 4. LLM Provider Configuration

Capella Agent supports nine LLM providers: **Anthropic Claude**, **OpenAI**, **Groq**, **DeepSeek**, **Mistral**, **OpenRouter**, **Google Gemini**, **Ollama**, and **Custom Endpoint**. You must configure at least one provider before using any agent.

### 4.1 Configure via Preferences UI

1. Open **Window > Preferences > Capella Agent > LLM Provider**.
2. Select a provider from the dropdown (Anthropic Claude, OpenAI, Groq, DeepSeek, Mistral, OpenRouter, Gemini, Ollama, or Custom Endpoint).
3. Enter your API key. The key is stored in Eclipse Equinox Secure Storage (encrypted, never in plain text).
4. Set model parameters:
   - **Model name** -- The model identifier (for example, `claude-sonnet-4-20250514` for Anthropic, `gpt-4o` for OpenAI, or `llama3` for Ollama). Leave blank to use the provider default.
   - **Temperature** -- Controls response randomness. Range: `0.0` to `2.0`. Default: `0.3`.
   - **Max tokens** -- Maximum tokens per response. Default: `4096`.
5. Click **Apply and Close**.

### 4.2 Configure via Environment Variables

You can override preferences with environment variables. Set these before launching Capella.

| Variable | Description | Default |
|---|---|---|
| `CAPELLA_AGENT_LLM_PROVIDER` | Active LLM provider ID (`anthropic`, `openai`, `groq`, `deepseek`, `mistral`, `openrouter`, `gemini`, `ollama`, `custom`) | `anthropic` |
| `CAPELLA_AGENT_LLM_API_KEY` | API key for the selected provider | *(none)* |
| `CAPELLA_AGENT_LLM_MODEL` | Model name | `claude-sonnet-4-20250514` |
| `CAPELLA_AGENT_LLM_TEMPERATURE` | Sampling temperature (`0.0`--`2.0`) | `0.3` |
| `CAPELLA_AGENT_LLM_MAX_TOKENS` | Maximum tokens per response | `4096` |
| `CAPELLA_AGENT_ACCESS_MODE` | `READ_ONLY` or `READ_WRITE` | `READ_ONLY` |
| `CAPELLA_AGENT_AUDIT_ENABLED` | Enable audit logging (`true` / `false`) | `true` |

> **Precedence**: Environment variables override Eclipse preferences. If both are set, the environment variable wins.

### 4.3 Provider-Specific Notes

**Anthropic Claude**
- Requires an API key from [console.anthropic.com](https://console.anthropic.com/).
- Provider ID: `anthropic`.
- Default model: `claude-sonnet-4-20250514`.
- API endpoint: `https://api.anthropic.com/v1/messages`.

**OpenAI**
- Requires an API key from [platform.openai.com](https://platform.openai.com/).
- Provider ID: `openai`.
- Configure a model name such as `gpt-4o` or `gpt-4o-mini`.

**Groq**
- Requires an API key from [console.groq.com](https://console.groq.com/).
- Provider ID: `groq`.
- Extends `OpenAiCompatibleProvider`. Configure a model name such as `llama-3.3-70b-versatile`.
- **Token limitation**: Groq imposes strict context window and rate limits on free-tier accounts. With 84 tools registered, the tool schema alone can consume a significant portion of the context window. If you experience `413 Request Entity Too Large` or truncated responses, switch to Claude or OpenAI, which support larger context windows. Alternatively, use Groq only with a subset of tools by installing fewer agent plugins.

**DeepSeek**
- Requires an API key from [platform.deepseek.com](https://platform.deepseek.com/).
- Provider ID: `deepseek`.
- Extends `OpenAiCompatibleProvider`. Configure a model name such as `deepseek-chat`.

**Mistral**
- Requires an API key from [console.mistral.ai](https://console.mistral.ai/).
- Provider ID: `mistral`.
- Extends `OpenAiCompatibleProvider`. Configure a model name such as `mistral-large-latest`.

**OpenRouter**
- Requires an API key from [openrouter.ai](https://openrouter.ai/).
- Provider ID: `openrouter`.
- Extends `OpenAiCompatibleProvider`. Supports any model available on the OpenRouter marketplace.

**Google Gemini**
- Requires an API key from [aistudio.google.com](https://aistudio.google.com/).
- Provider ID: `gemini`.
- Extends `OpenAiCompatibleProvider`. Configure a model name such as `gemini-2.0-flash`.

**Ollama (local)**
- No API key required. Ollama runs locally.
- Provider ID: `ollama`.
- Ensure the Ollama server is running at `http://localhost:11434` before sending queries.
- Pull a model first: `ollama pull llama3`.

**Custom Endpoint**
- Provider ID: `custom`.
- Extends `OpenAiCompatibleProvider`. Point to any OpenAI-compatible API by setting the endpoint URL in preferences.
- Useful for self-hosted models behind corporate proxies or for providers not yet built in.

---

## 5. Teamcenter Configuration

The Teamcenter agent connects to Siemens Teamcenter via the Active Workspace REST gateway. This section is only required if you use the Teamcenter integration tools.

### 5.1 Configure via Preferences UI

1. Open **Window > Preferences > Capella Agent > Teamcenter**.
2. Fill in the connection fields:
   - **Gateway URL** -- The Teamcenter Active Workspace REST endpoint (for example, `https://tc-aw.company.com/tc`). Default: `http://localhost:7001/tc`.
   - **Authentication method** -- Select `basic` (username/password) or `sso` (single sign-on via browser redirect).
   - **Username** -- Your Teamcenter username.
   - **Password** -- Stored in Eclipse Secure Storage (encrypted).
3. Click **Test Connection** to verify connectivity.
4. Click **Apply and Close**.

### 5.2 Connection Requirements

- The Teamcenter Active Workspace server must be reachable from the machine running Capella.
- For SSO authentication, a browser window opens during the first connection to complete the identity provider flow.
- The REST client uses `java.net.http.HttpClient` and respects proxy settings configured in **Window > Preferences > General > Network Connections**.

---

## 6. Simulation Configuration

The Simulation agent bridges Capella models with external simulation engines. Currently, MATLAB is supported through two engine bridges: the MATLAB Engine API (in-process) and a MATLAB Command bridge (subprocess).

### 6.1 Configure via Preferences UI

1. Open **Window > Preferences > Capella Agent > Simulation**.
2. Configure:
   - **MATLAB installation path** -- The root directory of your MATLAB installation (for example, `C:\Program Files\MATLAB\R2024a` or `/usr/local/MATLAB/R2024a`). Alternatively, set the `MATLAB_HOME` environment variable.
   - **Working directory** -- A directory for temporary simulation files. Default: the system temp directory.
   - **Timeout** -- Maximum execution time in seconds before a simulation run is cancelled. Default: `300`.
3. Click **Apply and Close**.

### 6.2 MATLAB Engine Setup

For the in-process MATLAB Engine bridge to work:

1. MATLAB R2022b or later must be installed.
2. Add the MATLAB Engine JAR to the classpath: `$MATLAB_HOME/extern/engines/java/jar/engine.jar`.
3. The `matlab.engine` module must be accessible to the JVM.

> **Tip**: If you do not have MATLAB installed, the Simulation agent gracefully reports that no engines are available. The Model Chat and Teamcenter agents remain fully functional.

---

## 7. Security and Access Control

Capella Agent enforces a two-tier access model to protect your Capella models from unintended modifications.

### 7.1 Access Modes

| Mode | Description |
|---|---|
| `READ_ONLY` | The agent can query, search, and inspect model elements. Any attempt to create, update, or delete elements is rejected. **This is the default.** |
| `READ_WRITE` | The agent can read and write model data. Write operations execute within EMF transactions and appear in the undo/redo history. Every mutation is logged in the audit trail. |

Change the access mode in **Window > Preferences > Capella Agent > LLM Provider** or by setting the `CAPELLA_AGENT_ACCESS_MODE` environment variable.

### 7.2 Audit Logging

When audit logging is enabled (the default), every tool invocation and model change is recorded with a timestamp, action type, tool name, arguments, and success/failure status. Entries are written in structured JSON format.

**Audit log location**:

```
<workspace>/.metadata/.plugins/com.capellaagent.core/audit.log
```

Example audit entry:

```
AUDIT [2025-03-15T10:23:45.123+01:00] tool.execute: {"tool":"ListElements","arguments":"{\"layer\":\"SA\"}","success":true,"message":"completed"}
```

### 7.3 API Key Storage

API keys are encrypted using Eclipse Equinox Secure Storage (`org.eclipse.equinox.security`). Keys are never stored in plain text in preference files or workspace metadata.

- Keys are stored under the secure node `com.capellaagent.core/apikeys`.
- Teamcenter passwords are stored under `/com/capellaagent/teamcenter`.

---

## 8. Claude Code MCP Integration

The `com.capellaagent.mcp` plugin exposes all registered Capella Agent tools to Claude Code through the Model Context Protocol (MCP). This lets you interact with a live Capella model from the Claude Code terminal without opening the in-Capella chat views.

### 8.1 Prerequisites

- [Claude Code](https://docs.anthropic.com/en/docs/claude-code) installed and available on your `PATH`.
- Capella Agent plugins installed and Capella running with a model open.

### 8.2 How It Works

1. When Capella starts, the `McpActivator` launches an embedded HTTP endpoint on `localhost:9847`.
2. A `.mcp.json` file at the project root tells Claude Code how to start the bridge subprocess.
3. The bridge subprocess (`McpServerBridge` + `McpStdioTransport`) speaks MCP stdio (JSON-RPC 2.0) on its standard streams and relays tool calls to the Eclipse HTTP endpoint.
4. `McpToolAdapter` translates between MCP tool schemas and the internal `IToolDescriptor` / `IToolExecutor` contracts, so every tool registered in the `ToolRegistry` is automatically available to Claude Code.

```
Claude Code  <--stdio/JSON-RPC 2.0-->  Bridge subprocess  <--HTTP-->  Eclipse plugin
(terminal)                              (McpServerBridge)              (McpHttpEndpoint
                                                                       on port 9847)
```

### 8.3 Configuration

The `.mcp.json` file in the project root configures Claude Code to discover the MCP bridge. A working configuration is included in the repository. If you need to create or customize it, the file should look like this:

```json
{
  "mcpServers": {
    "capella-agent": {
      "command": "java",
      "args": [
        "-jar",
        "com.capellaagent.mcp/target/com.capellaagent.mcp-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "CAPELLA_MCP_PORT": "9847"
      }
    }
  }
}
```

Place this file at the root of the `capella-agent` project directory. Claude Code reads `.mcp.json` automatically when you run `claude` from that directory.

**Environment variable**:

| Variable | Description | Default |
|---|---|---|
| `CAPELLA_MCP_PORT` | Port for the MCP HTTP endpoint inside Eclipse | `9847` |

To use a non-default port, set `CAPELLA_MCP_PORT` before launching Capella and update the port reference in `.mcp.json` accordingly.

### 8.5 Verifying MCP Tool Registration

After starting Claude Code with Capella running, verify that all 84 tools are available:

1. In the Claude Code terminal, type: `/tools`
2. You should see all Capella Agent tools listed (search, list_elements, create_element, etc.).
3. If tools are missing, check that all Capella Agent bundles are active (see Troubleshooting below).

### 8.4 Using Claude Code with Capella

1. Start Capella and open a model.
2. Open a terminal in the `capella-agent` project directory (where `.mcp.json` lives).
3. Run `claude` to start a Claude Code session.
4. Claude Code auto-discovers the MCP tools. You can now ask questions like `List all system functions` and Claude Code calls the Capella tools directly.

---

## 9. Verification

After installation and configuration, verify that everything works.

### 9.1 Open a Capella Project

Open Capella with a sample `.aird` project. You need at least one model loaded for the agent tools to have content to query.

### 9.2 Open the Model Chat View

1. Navigate to **Window > Show View > Other...**.
2. Expand **Capella Agent** and select **AI Model Chat**.
3. Click **Open**.

### 9.3 Run a Test Query

Type the following in the chat input:

```
List all system functions
```

You should see a response listing functions from the active Capella model. If the model is empty, you will see a message indicating no elements were found.

### 9.4 Check the Audit Log

Open the audit log file at `<workspace>/.metadata/.plugins/com.capellaagent.core/audit.log` and verify that entries were recorded for your test query.

### 9.5 Verify Optional Integrations

**Teamcenter** -- Open the Teamcenter preferences and click **Test Connection**. A successful connection displays a confirmation message.

**Simulation** -- Open the AI Model Chat view and type `List available simulation engines`. If MATLAB is installed and configured, you should see it listed as an available engine.

---

## 10. Setup Checklist

Use this checklist to confirm your installation is complete.

- [ ] Eclipse Capella 7.0+ installed and running
- [ ] Java 17+ JDK available for building
- [ ] Repository cloned and `mvn clean verify` succeeds
- [ ] Plugins installed in Capella via the local update site
- [ ] Capella restarted after installation
- [ ] LLM provider configured with a valid API key
- [ ] Test query in AI Model Chat returns a response
- [ ] Audit log contains entries for the test query
- [ ] *(Optional)* Teamcenter connection tested and verified
- [ ] *(Optional)* MATLAB path configured and engine detected
- [ ] *(Optional)* Claude Code installed and `.mcp.json` present at project root
- [ ] *(Optional)* Capella running with MCP endpoint active on port 9847
- [ ] *(Optional)* Claude Code session connects and lists available tools

---

## 11. Troubleshooting

### "No LLM providers are registered"

The core bundle failed to activate or no provider bundles were loaded. Open **Help > About Eclipse Capella > Installation Details > Plug-ins** and verify that `com.capellaagent.core` is listed and active.

### "Anthropic API authentication failed"

Your API key is missing or invalid. Open **Window > Preferences > Capella Agent > LLM Provider** and re-enter your key. Verify the key works by testing it with `curl`:

```bash
curl -s https://api.anthropic.com/v1/messages \
  -H "x-api-key: YOUR_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d '{"model":"claude-sonnet-4-20250514","max_tokens":10,"messages":[{"role":"user","content":"hi"}]}'
```

### "No active Capella session"

You must have a `.aird` file open before using agent tools. Open or create a Capella project with a model.

### Ollama connection refused

Ensure the Ollama server is running:

```bash
ollama serve
```

Verify it is listening on port 11434:

```bash
curl http://localhost:11434/api/tags
```

### Teamcenter connection timeout

Check that the gateway URL is correct and the Active Workspace server is reachable. Verify proxy settings in **Window > Preferences > General > Network Connections** if you are behind a corporate proxy.

### MATLAB engine not detected

Verify that `MATLAB_HOME` is set correctly and that the MATLAB executable exists at `$MATLAB_HOME/bin/matlab` (Linux/macOS) or `$MATLAB_HOME\bin\matlab.exe` (Windows).

### "No tools registered" or tools list is empty

This means the agent bundles failed to activate and register their tools with the `ToolRegistry`. To diagnose:

1. Open **Help > About Eclipse Capella > Installation Details > Plug-ins**.
2. Verify that `com.capellaagent.core`, `com.capellaagent.modelchat`, and any other agent bundles are listed.
3. Check the **Status** column -- all Capella Agent bundles should show `ACTIVE`.
4. If a bundle shows `INSTALLED` or `RESOLVED` but not `ACTIVE`, it failed to start. Check the Eclipse error log (**Window > Show View > General > Error Log**) for the root cause.
5. Common causes: missing dependency (Capella platform not matching), class loading errors from incompatible JDK version, or a missing required bundle.

If using Claude Code via MCP and no tools appear, confirm that Capella is running and the MCP endpoint is active on port 9847. You can test the endpoint directly:

```bash
curl http://localhost:9847/health
```

### "Rate limit exceeded" or HTTP 429 errors

Your LLM provider is throttling requests. Options:

1. **Switch providers** -- change to a provider with higher rate limits (Claude or OpenAI typically offer higher throughput than free-tier Groq).
2. **Upgrade your tier** -- most providers offer higher rate limits on paid plans.
3. **Reduce request frequency** -- wait between complex queries that trigger multiple tool calls.
4. **Use Ollama for local inference** -- local providers have no rate limits.

### "No response from LLM" or empty responses

The LLM provider is not responding. Check the following:

1. **API key**: Open **Window > Preferences > Capella Agent > LLM Provider** and verify your API key is set and valid.
2. **Network connectivity**: Ensure your machine can reach the provider's API endpoint (check proxy settings in **Window > Preferences > General > Network Connections**).
3. **Provider status**: Check the provider's status page (e.g., [status.anthropic.com](https://status.anthropic.com/) for Claude).
4. **Model name**: Verify the model name is valid for your provider. An incorrect model name can cause silent failures.
5. **Max tokens**: Ensure `CAPELLA_AGENT_LLM_MAX_TOKENS` is set to a reasonable value (default 4096).

### Tools returning generic advice instead of model-specific results

If the agent responds with general systems engineering advice rather than data from your actual Capella model, the tools are not being called by the LLM. This is a distinct problem from "no tools registered" -- the tools exist but the LLM is choosing not to use them.

Possible causes and fixes:

1. **Bundle activation**: Verify all agent bundles are active (see "No tools registered" above). If tools are not registered, the LLM has no tools to call.
2. **No active model session**: Ensure you have a `.aird` file open in Capella. Without an active model, tools report no data and the LLM falls back to general knowledge.
3. **Provider capabilities**: Some smaller LLM models have weak tool-calling support. Use `claude-sonnet-4-20250514`, `gpt-4o`, or a similarly capable model.
4. **Too many tools**: With 84 tools registered, some providers with small context windows may truncate the tool list. If using Groq or a small Ollama model, consider installing only the agent bundles you need, or switch to a provider with a larger context window.

### MCP bridge connection refused

If Claude Code reports that it cannot connect to the MCP bridge:

1. Confirm Capella is running and a model is open.
2. Check that port 9847 is not blocked by a firewall or occupied by another process.
3. Verify the `.mcp.json` file exists at the project root and contains valid configuration.
4. Check the Capella error log for MCP endpoint startup errors.
5. Try restarting Capella -- the MCP endpoint starts during bundle activation.
