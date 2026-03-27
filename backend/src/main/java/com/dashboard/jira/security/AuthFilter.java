package com.dashboard.jira.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that protects all /api/** endpoints (except /api/auth/**)
 * by requiring a valid JWT in the Authorization header.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthFilter implements Filter {

    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        String method = httpReq.getMethod();

        // Allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            httpRes.setHeader("Access-Control-Allow-Origin", httpReq.getHeader("Origin"));
            httpRes.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            httpRes.setHeader("Access-Control-Allow-Headers", "*");
            httpRes.setHeader("Access-Control-Allow-Credentials", "true");
            httpRes.setHeader("Access-Control-Max-Age", "3600");
            httpRes.setStatus(200);
            return;
        }

        // Allow auth endpoints without token
        if (path.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }

        // All other /api/** paths require auth
        if (path.startsWith("/api/")) {
            // Add CORS headers so the browser can read error responses
            String origin = httpReq.getHeader("Origin");
            if (origin != null) {
                httpRes.setHeader("Access-Control-Allow-Origin", origin);
                httpRes.setHeader("Access-Control-Allow-Credentials", "true");
            }

            String authHeader = httpReq.getHeader("Authorization");
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // Fallback: accept token as query param (for <img src>, <a href>, etc.)
            if (token == null) {
                token = httpReq.getParameter("token");
            }

            if (token == null || token.isBlank()) {
                httpRes.setStatus(401);
                httpRes.setContentType("application/json");
                httpRes.getWriter().write("{\"error\":\"Authentication required. Please log in.\"}");
                return;
            }

            if (!jwtUtil.isValid(token)) {
                httpRes.setStatus(401);
                httpRes.setContentType("application/json");
                httpRes.getWriter().write("{\"error\":\"Session expired. Please log in again.\"}");
                return;
            }

            // Store credentials in request attributes so services can access them
            httpReq.setAttribute("jiraUsername", jwtUtil.getUsername(token));
            httpReq.setAttribute("jiraApiToken", jwtUtil.getApiToken(token));
            httpReq.setAttribute("jiraBaseUrl", jwtUtil.getJiraBaseUrl(token));
            httpReq.setAttribute("jiraDisplayName", jwtUtil.getDisplayName(token));
        }

        chain.doFilter(request, response);
    }
}
