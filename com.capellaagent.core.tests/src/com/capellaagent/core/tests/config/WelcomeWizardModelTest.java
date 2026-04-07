package com.capellaagent.core.tests.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.capellaagent.core.config.WelcomeWizardModel;
import com.capellaagent.core.config.WelcomeWizardModel.ConnectionTestResult;
import com.capellaagent.core.config.WelcomeWizardModel.Page;

/**
 * Unit tests for {@link WelcomeWizardModel}.
 */
class WelcomeWizardModelTest {

    @Test
    @DisplayName("default selected provider is Claude")
    void defaultProviderIsClaude() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        assertEquals("anthropic", m.selectedProviderId());
        assertEquals("Claude (Anthropic)", m.selectedProvider().displayName());
    }

    @Test
    @DisplayName("default page is PICK_PROVIDER")
    void defaultPageIsPickProvider() {
        assertEquals(Page.PICK_PROVIDER, new WelcomeWizardModel().currentPage());
    }

    @Test
    @DisplayName("applicable pages for cloud provider include ENTER_API_KEY")
    void cloudProviderIncludesApiKeyPage() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("openai");
        assertTrue(m.applicablePages().contains(Page.ENTER_API_KEY));
        assertTrue(m.applicablePages().contains(Page.TEST_CONNECTION));
    }

    @Test
    @DisplayName("applicable pages for Ollama skip ENTER_API_KEY")
    void ollamaSkipsApiKeyPage() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("ollama");
        assertTrue(m.isOfflineProvider());
        assertFalse(m.applicablePages().contains(Page.ENTER_API_KEY),
            "Ollama page flow should skip API key step");
    }

    @Test
    @DisplayName("next() skips non-applicable pages")
    void nextSkipsNonApplicable() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("ollama"); // offline
        assertEquals(Page.PICK_PROVIDER, m.currentPage());
        // Next should skip ENTER_API_KEY (not applicable) and jump to TEST_CONNECTION
        assertEquals(Page.TEST_CONNECTION, m.next());
        assertNull(m.next(), "past last page returns null");
    }

    @Test
    @DisplayName("hasRequiredConfig: cloud needs API key")
    void hasRequiredConfigCloud() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("openai");
        assertFalse(m.hasRequiredConfig(), "no key = not ready");
        m.setApiKey("sk-test");
        assertTrue(m.hasRequiredConfig());
    }

    @Test
    @DisplayName("hasRequiredConfig: offline always ready")
    void hasRequiredConfigOffline() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("ollama");
        assertTrue(m.hasRequiredConfig());
    }

    @Test
    @DisplayName("setApiKey resets the test result")
    void setApiKeyResetsTestResult() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("openai");
        m.setApiKey("first");
        m.setLastTestResult(ConnectionTestResult.SUCCESS);
        m.setApiKey("second");
        assertEquals(ConnectionTestResult.UNTESTED, m.lastTestResult());
    }

    @Test
    @DisplayName("setSelectedProviderId resets the test result")
    void setProviderResetsTestResult() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setLastTestResult(ConnectionTestResult.SUCCESS);
        m.setSelectedProviderId("openai");
        assertEquals(ConnectionTestResult.UNTESTED, m.lastTestResult());
    }

    @Test
    @DisplayName("canFinish: offline always true")
    void canFinishOffline() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("ollama");
        assertTrue(m.canFinish());
    }

    @Test
    @DisplayName("canFinish: cloud requires successful test")
    void canFinishCloud() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("openai");
        m.setApiKey("sk-test");
        assertFalse(m.canFinish());
        m.setLastTestResult(ConnectionTestResult.SUCCESS);
        assertTrue(m.canFinish());
    }

    @Test
    @DisplayName("unknown provider id throws")
    void unknownProviderThrows() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        assertThrows(IllegalArgumentException.class,
            () -> m.setSelectedProviderId("bogus"));
    }

    @Test
    @DisplayName("PROVIDERS catalog includes the 5 documented options")
    void providersCatalogComplete() {
        assertEquals(5, WelcomeWizardModel.PROVIDERS.size());
        assertTrue(WelcomeWizardModel.PROVIDERS.stream()
            .anyMatch(p -> p.id().equals("ollama") && p.offline()));
    }

    @Test
    @DisplayName("isLastPage: true when at TEST_CONNECTION")
    void isLastPageAtTestConnection() {
        WelcomeWizardModel m = new WelcomeWizardModel();
        m.setSelectedProviderId("ollama");
        m.next(); // -> TEST_CONNECTION
        assertTrue(m.isLastPage());
    }
}
