package com.dashboard.jira.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Creates per-request JIRA WebClients using the logged-in user's credentials.
 * Replaces the old singleton jiraWebClient for authenticated operations.
 */
@Component
public class UserWebClientFactory {

    private final ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    /**
     * Build a JIRA WebClient using user credentials from the current HTTP request.
     */
    public WebClient getJiraWebClient() {
        HttpServletRequest request = getCurrentRequest();

        String baseUrl = (String) request.getAttribute("jiraBaseUrl");
        String username = (String) request.getAttribute("jiraUsername");
        String apiToken = (String) request.getAttribute("jiraApiToken");

        if (baseUrl == null || username == null || apiToken == null) {
            throw new RuntimeException("No JIRA credentials found in request. User not authenticated.");
        }

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Get the JIRA base URL from the current request's auth context.
     */
    public String getJiraBaseUrl() {
        return (String) getCurrentRequest().getAttribute("jiraBaseUrl");
    }

    /**
     * Get the logged-in user's username from the current request's auth context.
     */
    public String getUsername() {
        return (String) getCurrentRequest().getAttribute("jiraUsername");
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new RuntimeException("No servlet request context available.");
        }
        return attrs.getRequest();
    }
}
