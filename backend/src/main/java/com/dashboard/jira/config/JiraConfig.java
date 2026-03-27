package com.dashboard.jira.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class JiraConfig {

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.api-token:}")
    private String jiraApiToken;

    @Bean(name = "jiraWebClient")
    public WebClient jiraWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(jiraBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .exchangeStrategies(strategies);

        // Use Bearer token if configured (fallback client)
        if (jiraApiToken != null && !jiraApiToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + jiraApiToken);
        }

        return builder.build();
    }
}
