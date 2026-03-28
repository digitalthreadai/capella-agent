package com.capellaagent.core.llm.providers;

/**
 * LLM provider for GitHub Models — free API access to GPT-4o, Claude,
 * Llama, and other models via an OpenAI-compatible endpoint.
 * <p>
 * Authentication uses a GitHub Personal Access Token (PAT).
 * Generate one at <a href="https://github.com/settings/tokens">github.com/settings/tokens</a>
 * with no special scopes required.
 * <p>
 * Available models include:
 * <ul>
 *   <li>{@code gpt-4o} — GPT-4o (default)</li>
 *   <li>{@code gpt-4o-mini} — GPT-4o Mini (faster, cheaper)</li>
 *   <li>{@code o3-mini} — OpenAI o3-mini reasoning model</li>
 *   <li>{@code Meta-Llama-3.1-405B-Instruct} — Llama 3.1 405B</li>
 *   <li>{@code Mistral-Large-2411} — Mistral Large</li>
 *   <li>{@code AI21-Jamba-1.5-Large} — AI21 Jamba</li>
 * </ul>
 * <p>
 * Token limits are significantly higher than Groq free tier,
 * making this an excellent free option for the Capella Agent.
 *
 * @see <a href="https://github.com/marketplace/models">GitHub Models Marketplace</a>
 */
public class GitHubModelsProvider extends OpenAiCompatibleProvider {

    @Override
    public String getId() {
        return "github-models";
    }

    @Override
    public String getDisplayName() {
        return "GitHub Models (Free)";
    }

    @Override
    protected String getDefaultApiUrl() {
        return "https://models.inference.ai.dev/chat/completions";
    }

    @Override
    protected String getDefaultModel() {
        return "gpt-4o";
    }

    @Override
    protected String getApiKeyId() {
        return "github-models";
    }
}
