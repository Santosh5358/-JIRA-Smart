package com.dashboard.jira.controller;

import com.dashboard.jira.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jira.base-url}")
    private String defaultJiraBaseUrl;

    /**
     * POST /api/auth/login
     * Validates JIRA credentials (username + personal access token) and returns a JWT.
     *
     * Body: { "username": "john.doe", "token": "your-jira-pat" }
     * Optional: { ..., "jiraBaseUrl": "https://jira.company.com/" }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String token = credentials.get("token");
        String jiraBaseUrl = credentials.getOrDefault("jiraBaseUrl", defaultJiraBaseUrl);

        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Username and API token are required."
            ));
        }

        log.info("Login attempt for: {}", username);

        try {
            // Validate credentials by calling JIRA /rest/api/2/myself with Bearer token
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                    .build();

            WebClient testClient = WebClient.builder()
                    .baseUrl(jiraBaseUrl)
                    .defaultHeader("Authorization", "Bearer " + token)
                    .defaultHeader("Content-Type", "application/json")
                    .defaultHeader("Accept", "application/json")
                    .exchangeStrategies(strategies)
                    .build();

            String response = testClient.get()
                    .uri("/rest/api/2/myself")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode user = objectMapper.readTree(response);
            String displayName = user.has("displayName") ? user.get("displayName").asText() : username;
            String avatarUrl = user.has("avatarUrls") && user.get("avatarUrls").has("48x48")
                    ? user.get("avatarUrls").get("48x48").asText() : "";

            String emailAddress = user.has("emailAddress") ? user.get("emailAddress").asText() : username;

            // Generate JWT with Bearer token credentials
            String jwt = jwtUtil.generateToken(username, token, displayName, jiraBaseUrl);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", jwt);
            result.put("displayName", displayName);
            result.put("email", emailAddress);
            result.put("username", username);
            result.put("avatarUrl", avatarUrl);

            log.info("Login successful for: {} ({})", username, displayName);
            return ResponseEntity.ok(result);

        } catch (WebClientResponseException e) {
            log.warn("Login failed for {}: {} - {}", username, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Invalid JIRA credentials. Check your username and API token."
            ));
        } catch (Exception e) {
            log.error("Login error for {}: {}", username, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Could not connect to JIRA: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /api/auth/validate
     * Check if a JWT token is still valid.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(Map.of("valid", false));
        }

        String token = authHeader.substring(7);
        boolean valid = jwtUtil.isValid(token);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);
        if (valid) {
            result.put("username", jwtUtil.getUsername(token));
            result.put("displayName", jwtUtil.getDisplayName(token));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/auth/health
     * Health check endpoint for Docker / load balancer probes.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "jira-smart-dashboard",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
