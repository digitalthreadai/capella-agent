package com.capellaagent.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data model for the first-launch welcome wizard.
 * <p>
 * The UI layer (SWT {@code WizardDialog} — landed in Week 6) binds to this
 * model. Keeping the state + validation pure-Java means the wizard flow can
 * be unit-tested without spinning up Eclipse.
 *
 * <h2>Flow</h2>
 * Three linear pages:
 * <ol>
 *   <li>{@link Page#PICK_PROVIDER} — radio buttons, default Claude</li>
 *   <li>{@link Page#ENTER_API_KEY} — skipped automatically if the user picked
 *       an offline provider (Ollama)</li>
 *   <li>{@link Page#TEST_CONNECTION} — single "Test" button; on success the
 *       wizard auto-advances to the finish state</li>
 * </ol>
 * {@link #next()} / {@link #previous()} move through the pages, skipping any
 * that {@link Page#isApplicable} reports as not applicable for the current
 * provider.
 */
public final class WelcomeWizardModel {

    /** A step in the wizard flow. */
    public enum Page {
        PICK_PROVIDER {
            @Override public boolean isApplicable(WelcomeWizardModel m) { return true; }
        },
        ENTER_API_KEY {
            @Override public boolean isApplicable(WelcomeWizardModel m) {
                // Skip for offline providers
                return !m.isOfflineProvider();
            }
        },
        TEST_CONNECTION {
            @Override public boolean isApplicable(WelcomeWizardModel m) { return true; }
        };

        public abstract boolean isApplicable(WelcomeWizardModel m);
    }

    /** A provider the wizard offers on page 1. */
    public record ProviderOption(
            String id,
            String displayName,
            String oneLineHint,
            boolean offline,
            boolean requiresApiKey) {}

    /** The set of providers the wizard shows. */
    public static final List<ProviderOption> PROVIDERS = List.of(
        new ProviderOption("anthropic", "Claude (Anthropic)",
            "Recommended for accuracy. Needs API key.", false, true),
        new ProviderOption("openai", "OpenAI / GPT-4o",
            "Good general-purpose. Needs API key.", false, true),
        new ProviderOption("github", "GitHub Models (Free)",
            "Free tier via GitHub PAT. Token limits apply.", false, true),
        new ProviderOption("groq", "Groq (Fast)",
            "Very fast inference, free tier. Low token limits.", false, true),
        new ProviderOption("ollama", "Ollama (Local / Offline)",
            "Runs on your machine. No internet needed. No API key.", true, false)
    );

    private String selectedProviderId = "anthropic";
    private String apiKey = "";
    private String customModelId = "";
    private ConnectionTestResult lastTestResult = ConnectionTestResult.UNTESTED;
    private Page currentPage = Page.PICK_PROVIDER;

    public enum ConnectionTestResult {
        UNTESTED,
        TESTING,
        SUCCESS,
        FAILURE_AUTH,
        FAILURE_NETWORK,
        FAILURE_OTHER
    }

    // -- Provider selection --

    public String selectedProviderId() { return selectedProviderId; }

    public void setSelectedProviderId(String id) {
        if (id == null || id.isEmpty()) return;
        boolean known = PROVIDERS.stream().anyMatch(p -> p.id().equals(id));
        if (!known) {
            throw new IllegalArgumentException("unknown provider id: " + id);
        }
        this.selectedProviderId = id;
        // Reset test result — changing provider invalidates any previous test
        this.lastTestResult = ConnectionTestResult.UNTESTED;
    }

    public ProviderOption selectedProvider() {
        return PROVIDERS.stream()
            .filter(p -> p.id().equals(selectedProviderId))
            .findFirst()
            .orElseThrow();
    }

    public boolean isOfflineProvider() {
        return selectedProvider().offline();
    }

    // -- API key --

    public String apiKey() { return apiKey; }

    public void setApiKey(String key) {
        this.apiKey = key == null ? "" : key;
        this.lastTestResult = ConnectionTestResult.UNTESTED;
    }

    /**
     * Returns true if the user has supplied enough config to attempt a test
     * on the currently selected provider. Offline providers always pass;
     * cloud providers need a non-empty API key.
     */
    public boolean hasRequiredConfig() {
        ProviderOption p = selectedProvider();
        if (!p.requiresApiKey()) return true;
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    // -- Custom model --

    public String customModelId() { return customModelId; }

    public void setCustomModelId(String id) {
        this.customModelId = id == null ? "" : id;
    }

    // -- Test state --

    public ConnectionTestResult lastTestResult() { return lastTestResult; }

    public void setLastTestResult(ConnectionTestResult result) {
        this.lastTestResult = result == null ? ConnectionTestResult.UNTESTED : result;
    }

    // -- Page navigation --

    public Page currentPage() { return currentPage; }

    /**
     * Advances to the next applicable page. Returns the new current page,
     * or null if the wizard is complete.
     */
    public Page next() {
        List<Page> pages = applicablePages();
        int idx = pages.indexOf(currentPage);
        if (idx == -1 || idx + 1 >= pages.size()) {
            return null; // finished
        }
        currentPage = pages.get(idx + 1);
        return currentPage;
    }

    /**
     * Goes back to the previous applicable page. Returns the new current
     * page, or the first page if already there.
     */
    public Page previous() {
        List<Page> pages = applicablePages();
        int idx = pages.indexOf(currentPage);
        if (idx <= 0) return pages.get(0);
        currentPage = pages.get(idx - 1);
        return currentPage;
    }

    /** Returns the list of pages applicable given the current provider. */
    public List<Page> applicablePages() {
        List<Page> applicable = new ArrayList<>();
        for (Page p : Page.values()) {
            if (p.isApplicable(this)) applicable.add(p);
        }
        return applicable;
    }

    /**
     * Returns true if the wizard can finish (either test passed or user
     * picked offline and did not fail a test).
     */
    public boolean canFinish() {
        if (isOfflineProvider()) return true;
        return lastTestResult == ConnectionTestResult.SUCCESS;
    }

    /**
     * Returns true if the wizard is on the last applicable page.
     */
    public boolean isLastPage() {
        List<Page> pages = applicablePages();
        return pages.isEmpty() || pages.get(pages.size() - 1) == currentPage;
    }
}
