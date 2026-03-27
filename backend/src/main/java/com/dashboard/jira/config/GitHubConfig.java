package com.dashboard.jira.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GitHubConfig {

    @Value("${github.base-url}")
    private String githubBaseUrl;

    @Value("${github.api-token}")
    private String githubApiToken;

    @Bean(name = "gitHubWebClient")
    public WebClient gitHubWebClient() {
        // Convert web URL to API URL:  https://github.com/org/ → https://api.github.com
        // For GHE: https://github.dedalus.com/ → https://github.dedalus.com/api/v3
        String apiUrl = buildApiUrl(githubBaseUrl);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + githubApiToken)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .exchangeStrategies(strategies)
                .build();
    }

    private String buildApiUrl(String baseUrl) {
        String cleaned = baseUrl.replaceAll("/+$", "");
        // Public GitHub:  https://github.com/anything → https://api.github.com
        if (cleaned.contains("github.com") && !cleaned.contains("api.github.com")) {
            // Check if this is GHE or public
            java.net.URI uri = java.net.URI.create(cleaned);
            if ("github.com".equalsIgnoreCase(uri.getHost())) {
                return "https://api.github.com";
            }
            // GitHub Enterprise: https://ghe.company.com → https://ghe.company.com/api/v3
            return uri.getScheme() + "://" + uri.getHost() + "/api/v3";
        }
        return cleaned;
    }
}
