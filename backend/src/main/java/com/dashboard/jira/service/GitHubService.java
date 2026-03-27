package com.dashboard.jira.service;

import com.dashboard.jira.dto.PullRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GitHubService {

    private final WebClient gitHubWebClient;
    private final ObjectMapper objectMapper;

    @Value("${github.default-org:}")
    private String defaultOrg;

    public GitHubService(@Qualifier("gitHubWebClient") WebClient gitHubWebClient) {
        this.gitHubWebClient = gitHubWebClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Given a list of GitHub PR URLs, fetch each PR's details and file diffs.
     */
    public List<PullRequestDto> fetchPullRequests(List<String> prUrls) {
        List<PullRequestDto> results = new ArrayList<>();
        for (String url : prUrls) {
            try {
                PullRequestDto pr = fetchPullRequest(url);
                if (pr != null) {
                    results.add(pr);
                }
            } catch (Exception e) {
                log.warn("Could not fetch PR from URL {}: {}", url, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Parse a GitHub PR URL and fetch its details + file diffs.
     * Supports: https://github.com/owner/repo/pull/123
     *           https://ghe.company.com/owner/repo/pull/123
     */
    public PullRequestDto fetchPullRequest(String prUrl) {
        // Parse: .../owner/repo/pull/123
        Pattern pattern = Pattern.compile("/([^/]+)/([^/]+)/pull/(\\d+)");
        Matcher matcher = pattern.matcher(prUrl);
        if (!matcher.find()) {
            log.warn("Could not parse PR URL: {}", prUrl);
            return null;
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);
        int prNumber = Integer.parseInt(matcher.group(3));

        return fetchPullRequest(owner, repo, prNumber);
    }

    /**
     * Fetch PR details and file diffs from GitHub API.
     */
    public PullRequestDto fetchPullRequest(String owner, String repo, int prNumber) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching PR #{} from {}", prNumber, repoFullName);

        try {
            // 1. Fetch PR metadata
            String prResponse = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode prNode = objectMapper.readTree(prResponse);

            PullRequestDto.PullRequestDtoBuilder builder = PullRequestDto.builder()
                    .repoFullName(repoFullName)
                    .prNumber(prNumber)
                    .title(textVal(prNode, "title"))
                    .state(determinePrState(prNode))
                    .author(prNode.has("user") ? textVal(prNode.get("user"), "login") : "unknown")
                    .url(textVal(prNode, "html_url"))
                    .branch(prNode.has("head") ? textVal(prNode.get("head"), "ref") : "")
                    .targetBranch(prNode.has("base") ? textVal(prNode.get("base"), "ref") : "")
                    .additions(prNode.has("additions") ? prNode.get("additions").asInt() : 0)
                    .deletions(prNode.has("deletions") ? prNode.get("deletions").asInt() : 0)
                    .changedFiles(prNode.has("changed_files") ? prNode.get("changed_files").asInt() : 0);

            // 2. Fetch files changed (with patches/diffs)
            List<PullRequestDto.FileChangeDto> files = fetchPrFiles(owner, repo, prNumber);
            builder.files(files);

            return builder.build();

        } catch (WebClientResponseException e) {
            log.error("GitHub API error for PR #{} in {}: {} - {}", prNumber, repoFullName,
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Error fetching PR #{} from {}", prNumber, repoFullName, e);
            return null;
        }
    }

    /**
     * Fetch files changed in a PR (with patch content).
     */
    private List<PullRequestDto.FileChangeDto> fetchPrFiles(String owner, String repo, int prNumber) {
        List<PullRequestDto.FileChangeDto> files = new ArrayList<>();
        try {
            String response = gitHubWebClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, prNumber)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode filesNode = objectMapper.readTree(response);
            if (filesNode.isArray()) {
                for (JsonNode fileNode : filesNode) {
                    String patch = textVal(fileNode, "patch");
                    // Truncate very large patches to avoid blowing up the AI prompt
                    if (patch.length() > 3000) {
                        patch = patch.substring(0, 3000) + "\n... (truncated, " + patch.length() + " chars total)";
                    }

                    files.add(PullRequestDto.FileChangeDto.builder()
                            .filename(textVal(fileNode, "filename"))
                            .status(textVal(fileNode, "status"))
                            .additions(fileNode.has("additions") ? fileNode.get("additions").asInt() : 0)
                            .deletions(fileNode.has("deletions") ? fileNode.get("deletions").asInt() : 0)
                            .patch(patch)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch files for PR #{} in {}/{}: {}", prNumber, owner, repo, e.getMessage());
        }
        return files;
    }

    /**
     * Search for PRs that mention a JIRA issue key in their title or branch name.
     * Uses GitHub search API.
     */
    public List<PullRequestDto> searchPrsByIssueKey(String issueKey, String orgName) {
        List<PullRequestDto> results = new ArrayList<>();
        String org = (orgName != null && !orgName.isEmpty()) ? orgName : defaultOrg;

        if (org == null || org.isEmpty()) {
            log.info("No GitHub org configured, skipping PR search for {}", issueKey);
            return results;
        }

        try {
            // Search PRs by issue key in title. Scope to the org.
            String query = issueKey + "+org:" + org + "+type:pr";
            log.info("Searching GitHub PRs with query: {}", query);

            String response = gitHubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/issues")
                            .queryParam("q", query)
                            .queryParam("per_page", 5)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String prUrl = textVal(item, "html_url");
                    if (prUrl.contains("/pull/")) {
                        PullRequestDto pr = fetchPullRequest(prUrl);
                        if (pr != null) {
                            results.add(pr);
                        }
                    }
                }
            }

            log.info("Found {} PRs for issue {}", results.size(), issueKey);
        } catch (Exception e) {
            log.warn("GitHub PR search failed for {}: {}", issueKey, e.getMessage());
        }

        return results;
    }

    private String determinePrState(JsonNode prNode) {
        boolean merged = prNode.has("merged") && prNode.get("merged").asBoolean();
        if (merged) return "merged";
        return textVal(prNode, "state");
    }

    private String textVal(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : "";
    }
}
