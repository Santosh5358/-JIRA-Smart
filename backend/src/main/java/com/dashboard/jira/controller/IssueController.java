package com.dashboard.jira.controller;

import com.dashboard.jira.dto.AnalysisResponseDto;
import com.dashboard.jira.dto.JiraIssueDto;
import com.dashboard.jira.service.AnalysisService;
import com.dashboard.jira.service.JiraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final JiraService jiraService;
    private final AnalysisService analysisService;

    /**
     * GET /api/issues/{issueKey}
     * Fetch full details for a single issue
     */
    @GetMapping("/{issueKey}")
    public ResponseEntity<JiraIssueDto> getIssueDetails(@PathVariable String issueKey) {
        log.info("Fetching details for issue: {}", issueKey);
        JiraIssueDto issue = jiraService.getIssueDetails(issueKey);
        return ResponseEntity.ok(issue);
    }

    /**
     * GET /api/issues/{issueKey}/analyze
     * AI-powered analysis of an issue with full context
     */
    @GetMapping("/{issueKey}/analyze")
    public ResponseEntity<AnalysisResponseDto> analyzeIssue(@PathVariable String issueKey) {
        log.info("Analyzing issue: {}", issueKey);
        AnalysisResponseDto analysis = analysisService.analyzeIssue(issueKey);
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/issues/attachment/proxy?url=...
     * Proxy JIRA attachment content (images, files) through the backend
     * so the frontend can display them without needing JIRA auth headers.
     */
    @GetMapping("/attachment/proxy")
    public ResponseEntity<byte[]> proxyAttachment(@RequestParam String url,
                                                   @RequestParam(defaultValue = "application/octet-stream") String mimeType) {
        log.info("Proxying attachment: {}", url);
        byte[] content = jiraService.proxyAttachment(url);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setCacheControl("public, max-age=86400");
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
