package com.dashboard.jira.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:28800000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT containing the user's JIRA credentials (Bearer token).
     */
    public String generateToken(String username, String apiToken, String displayName, String jiraBaseUrl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("apiToken", apiToken);
        claims.put("displayName", displayName);
        claims.put("jiraBaseUrl", jiraBaseUrl);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Parse and validate a JWT, returning the claims.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if a token is valid (not expired, valid signature).
     */
    public boolean isValid(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getApiToken(String token) {
        return parseToken(token).get("apiToken", String.class);
    }

    public String getDisplayName(String token) {
        return parseToken(token).get("displayName", String.class);
    }

    public String getJiraBaseUrl(String token) {
        return parseToken(token).get("jiraBaseUrl", String.class);
    }
}
