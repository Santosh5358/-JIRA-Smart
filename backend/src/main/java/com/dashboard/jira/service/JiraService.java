package com.dashboard.jira.service;

import com.dashboard.jira.dto.DashboardResponseDto;
import com.dashboard.jira.dto.JiraIssueDto;
import com.dashboard.jira.dto.SprintTimesheetDto;
import com.dashboard.jira.security.UserWebClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JiraService {

    private final WebClient jiraWebClient;          // fallback/default client
    private final UserWebClientFactory userWebClientFactory;
    private final ObjectMapper objectMapper;

    public JiraService(@Qualifier("jiraWebClient") WebClient jiraWebClient,
                       UserWebClientFactory userWebClientFactory) {
        this.jiraWebClient = jiraWebClient;
        this.userWebClientFactory = userWebClientFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get the JIRA WebClient — uses per-user credentials when available,
     * falls back to the configured default otherwise.
     */
    private WebClient getClient() {
        try {
            return userWebClientFactory.getJiraWebClient();
        } catch (Exception e) {
            // Fallback to default configured client (e.g., during startup checks)
            return jiraWebClient;
        }
    }

    /**
     * Check if the JIRA connection is healthy by calling /rest/api/2/myself
     * Returns user display name if connected, throws if not.
     */
    public Map<String, Object> checkConnection() {
        try {
            String response = getClient().get()
                    .uri("/rest/api/2/myself")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode user = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            result.put("connected", true);
            result.put("displayName", user.has("displayName") ? user.get("displayName").asText() : "Unknown");
            result.put("emailAddress", user.has("emailAddress") ? user.get("emailAddress").asText() : "");
            result.put("username", user.has("name") ? user.get("name").asText() : "");
            result.put("accountId", user.has("accountId") ? user.get("accountId").asText() : "");
            result.put("avatarUrl", user.has("avatarUrls") && user.get("avatarUrls").has("48x48")
                    ? user.get("avatarUrls").get("48x48").asText() : "");
            // Additional user profile fields
            result.put("timeZone", user.has("timeZone") ? user.get("timeZone").asText() : "");
            result.put("locale", user.has("locale") ? user.get("locale").asText() : "");
            result.put("active", user.has("active") ? user.get("active").asBoolean() : true);
            result.put("serverInfo", getClient().get()
                    .uri("/rest/api/2/serverInfo")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());
            return result;

        } catch (WebClientResponseException e) {
            log.error("JIRA connection check failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            Map<String, Object> result = new HashMap<>();
            result.put("connected", false);
            result.put("error", "Authentication failed: " + e.getStatusCode());
            return result;
        } catch (Exception e) {
            log.error("JIRA connection check error", e);
            Map<String, Object> result = new HashMap<>();
            result.put("connected", false);
            result.put("error", "Connection failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Fetch all issues assigned to a specific user (stories, bugs, tasks, etc.)
     */
    public DashboardResponseDto getAssignedIssues(String assignee, String status, String issueType) {
        StringBuilder jql = new StringBuilder();
        jql.append("assignee = \"").append(assignee).append("\"");

        if (status != null && !status.isEmpty()) {
            jql.append(" AND status = \"").append(status).append("\"");
        }
        if (issueType != null && !issueType.isEmpty()) {
            jql.append(" AND issuetype = \"").append(issueType).append("\"");
        }

        jql.append(" ORDER BY updated DESC");

        String fields = "summary,description,issuetype,status,priority,assignee,reporter," +
                "created,updated,labels,components,attachment,comment,issuelinks,subtasks," +
                "project,sprint,customfield_10014,customfield_10001";

        try {
            String response = getClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/2/search")
                            .queryParam("jql", jql.toString())
                            .queryParam("fields", fields)
                            .queryParam("maxResults", 50)
                            .queryParam("expand", "renderedFields")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode issuesNode = root.get("issues");

            List<JiraIssueDto> issues = new ArrayList<>();
            int stories = 0, bugs = 0, tasks = 0, defects = 0, others = 0;

            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    JiraIssueDto dto = mapIssueNode(issueNode);
                    issues.add(dto);

                    switch (dto.getIssueType().toLowerCase()) {
                        case "story" -> stories++;
                        case "bug" -> bugs++;
                        case "defect" -> defects++;
                        case "task" -> tasks++;
                        default -> others++;
                    }
                }
            }

            return DashboardResponseDto.builder()
                    .totalIssues(issues.size())
                    .stories(stories)
                    .bugs(bugs)
                    .defects(defects)
                    .tasks(tasks)
                    .others(others)
                    .issues(issues)
                    .build();

        } catch (WebClientResponseException e) {
            log.error("JIRA API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch issues from JIRA: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching JIRA issues", e);
            throw new RuntimeException("Error processing JIRA response: " + e.getMessage());
        }
    }

    /**
     * Fetch a single issue with full details
     */
    public JiraIssueDto getIssueDetails(String issueKey) {
        String fields = "summary,description,issuetype,status,priority,assignee,reporter," +
                "created,updated,labels,components,attachment,comment,issuelinks,subtasks," +
                "project,sprint,customfield_10014,customfield_10001,customfield_10020";

        try {
            String response = getClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/2/issue/" + issueKey)
                            .queryParam("fields", fields)
                            .queryParam("expand", "renderedFields")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode issueNode = objectMapper.readTree(response);
            return mapIssueNode(issueNode);

        } catch (WebClientResponseException e) {
            log.error("JIRA API error for {}: {} - {}", issueKey, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch issue " + issueKey + " from JIRA");
        } catch (Exception e) {
            log.error("Error fetching issue {}", issueKey, e);
            throw new RuntimeException("Error processing issue " + issueKey);
        }
    }

    /**
     * Fetch epic details by epic key
     */
    public JiraIssueDto getEpicDetails(String epicKey) {
        return getIssueDetails(epicKey);
    }

    /**
     * Fetch remote links for an issue and extract GitHub PR URLs.
     * JIRA remote links contain GitHub PR URLs added by integrations.
     */
    public List<String> getGitHubPrUrls(String issueKey) {
        List<String> prUrls = new ArrayList<>();
        try {
            String response = getClient().get()
                    .uri("/rest/api/2/issue/" + issueKey + "/remotelink")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode links = objectMapper.readTree(response);
            if (links != null && links.isArray()) {
                for (JsonNode link : links) {
                    JsonNode obj = link.get("object");
                    if (obj != null && obj.has("url")) {
                        String url = obj.get("url").asText();
                        if (url.contains("github") && url.contains("/pull/")) {
                            prUrls.add(url);
                            log.info("Found GitHub PR link for {}: {}", issueKey, url);
                        }
                    }
                }
            }
        } catch (WebClientResponseException e) {
            log.warn("Could not fetch remote links for {}: {} - {}", issueKey,
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Error fetching remote links for {}: {}", issueKey, e.getMessage());
        }
        log.info("Found {} GitHub PR URLs in remote links for {}", prUrls.size(), issueKey);
        return prUrls;
    }

    /**
     * Maps a JIRA JSON issue node to our DTO
     */
    private JiraIssueDto mapIssueNode(JsonNode issueNode) {
        JsonNode fields = issueNode.get("fields");

        JiraIssueDto.JiraIssueDtoBuilder builder = JiraIssueDto.builder();

        builder.key(textValue(issueNode, "key"));
        builder.summary(textValue(fields, "summary"));
        builder.description(extractDescription(fields.get("description")));
        builder.issueType(fields.has("issuetype") && fields.get("issuetype").has("name")
                ? fields.get("issuetype").get("name").asText() : "Unknown");
        builder.status(fields.has("status") && fields.get("status").has("name")
                ? fields.get("status").get("name").asText() : "Unknown");
        builder.priority(fields.has("priority") && fields.get("priority") != null
                && fields.get("priority").has("name")
                ? fields.get("priority").get("name").asText() : "None");
        builder.assignee(fields.has("assignee") && fields.get("assignee") != null
                && fields.get("assignee").has("displayName")
                ? fields.get("assignee").get("displayName").asText() : "Unassigned");
        builder.reporter(fields.has("reporter") && fields.get("reporter") != null
                && fields.get("reporter").has("displayName")
                ? fields.get("reporter").get("displayName").asText() : "Unknown");
        builder.created(textValue(fields, "created"));
        builder.updated(textValue(fields, "updated"));

        // Project
        if (fields.has("project") && fields.get("project") != null) {
            builder.projectName(textValue(fields.get("project"), "name"));
            builder.projectKey(textValue(fields.get("project"), "key"));
        }

        // Epic link (customfield_10014 is common for epic link)
        if (fields.has("customfield_10014") && fields.get("customfield_10014") != null
                && !fields.get("customfield_10014").isNull()) {
            builder.epicKey(fields.get("customfield_10014").asText());
        }

        // Sprint
        if (fields.has("customfield_10020") && fields.get("customfield_10020") != null
                && fields.get("customfield_10020").isArray()
                && fields.get("customfield_10020").size() > 0) {
            JsonNode sprint = fields.get("customfield_10020").get(fields.get("customfield_10020").size() - 1);
            builder.sprintName(textValue(sprint, "name"));
        }

        // Acceptance Criteria (customfield_10001)
        if (fields.has("customfield_10001") && fields.get("customfield_10001") != null
                && !fields.get("customfield_10001").isNull()) {
            builder.acceptanceCriteria(extractDescription(fields.get("customfield_10001")));
        }

        // Labels
        List<String> labels = new ArrayList<>();
        if (fields.has("labels") && fields.get("labels").isArray()) {
            for (JsonNode label : fields.get("labels")) {
                labels.add(label.asText());
            }
        }
        builder.labels(labels);

        // Components
        List<String> components = new ArrayList<>();
        if (fields.has("components") && fields.get("components").isArray()) {
            for (JsonNode comp : fields.get("components")) {
                components.add(textValue(comp, "name"));
            }
        }
        builder.components(components);

        // Attachments
        List<JiraIssueDto.AttachmentDto> attachments = new ArrayList<>();
        if (fields.has("attachment") && fields.get("attachment").isArray()) {
            for (JsonNode att : fields.get("attachment")) {
                attachments.add(JiraIssueDto.AttachmentDto.builder()
                        .filename(textValue(att, "filename"))
                        .mimeType(textValue(att, "mimeType"))
                        .contentUrl(textValue(att, "content"))
                        .thumbnailUrl(att.has("thumbnail") ? textValue(att, "thumbnail") : "")
                        .author(att.has("author") && att.get("author").has("displayName")
                                ? att.get("author").get("displayName").asText() : "")
                        .created(textValue(att, "created"))
                        .size(att.has("size") ? att.get("size").asLong() : 0)
                        .build());
            }
        }
        builder.attachments(attachments);

        // Comments
        List<JiraIssueDto.CommentDto> comments = new ArrayList<>();
        if (fields.has("comment") && fields.get("comment").has("comments")
                && fields.get("comment").get("comments").isArray()) {
            for (JsonNode c : fields.get("comment").get("comments")) {
                comments.add(JiraIssueDto.CommentDto.builder()
                        .author(c.has("author") && c.get("author").has("displayName")
                                ? c.get("author").get("displayName").asText() : "Unknown")
                        .body(extractDescription(c.get("body")))
                        .created(textValue(c, "created"))
                        .build());
            }
        }
        builder.comments(comments);

        // Linked issues
        List<JiraIssueDto.LinkDto> linkedIssues = new ArrayList<>();
        if (fields.has("issuelinks") && fields.get("issuelinks").isArray()) {
            for (JsonNode link : fields.get("issuelinks")) {
                JsonNode typeNode = link.has("type") ? link.get("type") : null;
                String linkTypeName = typeNode != null && typeNode.has("name")
                        ? typeNode.get("name").asText() : "Related";

                if (link.has("outwardIssue") && link.get("outwardIssue") != null) {
                    JsonNode outward = link.get("outwardIssue");
                    String relDesc = typeNode != null && typeNode.has("outward")
                            ? typeNode.get("outward").asText() : linkTypeName;
                    linkedIssues.add(buildLinkDto(linkTypeName, "outward", relDesc, outward));
                }
                if (link.has("inwardIssue") && link.get("inwardIssue") != null) {
                    JsonNode inward = link.get("inwardIssue");
                    String relDesc = typeNode != null && typeNode.has("inward")
                            ? typeNode.get("inward").asText() : linkTypeName;
                    linkedIssues.add(buildLinkDto(linkTypeName, "inward", relDesc, inward));
                }
            }
        }
        builder.linkedIssues(linkedIssues);

        // Subtasks
        List<JiraIssueDto.SubtaskDto> subtasks = new ArrayList<>();
        if (fields.has("subtasks") && fields.get("subtasks").isArray()) {
            for (JsonNode st : fields.get("subtasks")) {
                JsonNode stFields = st.get("fields");
                subtasks.add(JiraIssueDto.SubtaskDto.builder()
                        .key(textValue(st, "key"))
                        .summary(stFields != null ? textValue(stFields, "summary") : "")
                        .status(stFields != null && stFields.has("status") && stFields.get("status").has("name")
                                ? stFields.get("status").get("name").asText() : "Unknown")
                        .issueType(stFields != null && stFields.has("issuetype") && stFields.get("issuetype").has("name")
                                ? stFields.get("issuetype").get("name").asText() : "Subtask")
                        .build());
            }
        }
        builder.subtasks(subtasks);

        return builder.build();
    }

    private JiraIssueDto.LinkDto buildLinkDto(String linkType, String direction, String relationshipDesc, JsonNode issueNode) {
        JsonNode linkFields = issueNode.has("fields") ? issueNode.get("fields") : null;
        return JiraIssueDto.LinkDto.builder()
                .linkType(linkType)
                .direction(direction)
                .relationshipDesc(relationshipDesc)
                .issueKey(textValue(issueNode, "key"))
                .issueSummary(linkFields != null ? textValue(linkFields, "summary") : "")
                .issueType(linkFields != null && linkFields.has("issuetype")
                        && linkFields.get("issuetype").has("name")
                        ? linkFields.get("issuetype").get("name").asText() : "Unknown")
                .issueStatus(linkFields != null && linkFields.has("status")
                        && linkFields.get("status").has("name")
                        ? linkFields.get("status").get("name").asText() : "Unknown")
                .priority(linkFields != null && linkFields.has("priority")
                        && linkFields.get("priority").has("name")
                        ? linkFields.get("priority").get("name").asText() : "")
                .build();
    }

    /**
     * Download text-based attachment content from JIRA.
     * Supports: .txt, .log, .csv, .json, .xml, .sql, .md, .yml, .yaml, .properties, .cfg, .ini, .html
     * Returns the text content, or null if not downloadable / too large.
     */
    public String downloadTextAttachment(String contentUrl, String mimeType, long size) {
        // Only download text-based files up to 100KB
        if (size > 100 * 1024) {
            log.debug("Attachment too large for text download: {} bytes", size);
            return null;
        }

        String mime = (mimeType != null) ? mimeType.toLowerCase() : "";
        boolean isText = mime.startsWith("text/")
                || mime.contains("json")
                || mime.contains("xml")
                || mime.contains("sql")
                || mime.contains("yaml")
                || mime.contains("csv")
                || mime.contains("javascript")
                || mime.contains("html")
                || mime.contains("markdown")
                || mime.contains("properties")
                || mime.contains("log");

        if (!isText) {
            return null;
        }

        try {
            return getClient()
                    .get()
                    .uri(URI.create(contentUrl))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("Failed to download attachment from {}: {}", contentUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Proxy an attachment's content URL — used by frontend to display images / download files.
     * Returns the raw bytes with original content type.
     */
    public byte[] proxyAttachment(String contentUrl) {
        try {
            return getClient()
                    .get()
                    .uri(URI.create(contentUrl))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(java.time.Duration.ofSeconds(15));
        } catch (Exception e) {
            log.warn("Failed to proxy attachment from {}: {}", contentUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Extract plain text from JIRA's Atlassian Document Format (ADF) or plain string
     */
    private String extractDescription(JsonNode descriptionNode) {
        if (descriptionNode == null || descriptionNode.isNull()) {
            return "";
        }
        if (descriptionNode.isTextual()) {
            return descriptionNode.asText();
        }
        // ADF format — recursively extract text
        StringBuilder sb = new StringBuilder();
        extractTextFromAdf(descriptionNode, sb);
        return sb.toString().trim();
    }

    private void extractTextFromAdf(JsonNode node, StringBuilder sb) {
        if (node == null) return;

        String type = node.has("type") ? node.get("type").asText() : "";

        // Handle code blocks — preserve {code:lang}...{code} markup
        if ("codeBlock".equals(type)) {
            String lang = "";
            if (node.has("attrs") && node.get("attrs").has("language")) {
                lang = ":" + node.get("attrs").get("language").asText();
            }
            sb.append("{code").append(lang).append("}\n");
            if (node.has("content") && node.get("content").isArray()) {
                for (JsonNode child : node.get("content")) {
                    if (child.has("text")) {
                        sb.append(child.get("text").asText());
                    }
                }
            }
            sb.append("\n{code}\n");
            return;
        }

        // Handle headings — convert to h1. h2. etc.
        if ("heading".equals(type)) {
            int level = node.has("attrs") && node.get("attrs").has("level")
                    ? node.get("attrs").get("level").asInt() : 1;
            sb.append("h").append(level).append(". ");
            if (node.has("content") && node.get("content").isArray()) {
                for (JsonNode child : node.get("content")) {
                    extractTextFromAdf(child, sb);
                }
            }
            sb.append("\n");
            return;
        }

        // Handle blockquote
        if ("blockquote".equals(type)) {
            sb.append("{quote}");
            if (node.has("content") && node.get("content").isArray()) {
                for (JsonNode child : node.get("content")) {
                    extractTextFromAdf(child, sb);
                }
            }
            sb.append("{quote}\n");
            return;
        }

        // Handle bullet / ordered list
        if ("bulletList".equals(type) || "orderedList".equals(type)) {
            String prefix = "bulletList".equals(type) ? "* " : "# ";
            if (node.has("content") && node.get("content").isArray()) {
                for (JsonNode listItem : node.get("content")) {
                    sb.append(prefix);
                    if (listItem.has("content") && listItem.get("content").isArray()) {
                        for (JsonNode child : listItem.get("content")) {
                            extractTextFromAdf(child, sb);
                        }
                    }
                }
            }
            return;
        }

        // Handle rule (horizontal line)
        if ("rule".equals(type)) {
            sb.append("----\n");
            return;
        }

        // Handle inline text with marks (bold, italic, code, etc.)
        if (node.has("text")) {
            String text = node.get("text").asText();
            if (node.has("marks") && node.get("marks").isArray()) {
                for (JsonNode mark : node.get("marks")) {
                    String markType = mark.has("type") ? mark.get("type").asText() : "";
                    switch (markType) {
                        case "strong": text = "*" + text + "*"; break;
                        case "em": text = "_" + text + "_"; break;
                        case "code": text = "{{" + text + "}}"; break;
                        case "strike": text = "-" + text + "-"; break;
                    }
                }
            }
            sb.append(text);
        }

        // Handle inline card (link)
        if ("inlineCard".equals(type) && node.has("attrs") && node.get("attrs").has("url")) {
            sb.append("[").append(node.get("attrs").get("url").asText()).append("]");
        }

        // Recurse into children (for paragraph, tableRow, tableCell, etc.)
        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                extractTextFromAdf(child, sb);
            }
            // Add newline after paragraphs and table cells
            if ("paragraph".equals(type)) {
                sb.append("\n");
            }
        }
    }

    private String textValue(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return "";
    }

    /**
     * Format seconds into human-readable time like "6 hours", "1 hour", "30 minutes"
     */
    private String formatTimeSpent(long seconds) {
        if (seconds <= 0) return "0";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0 && minutes > 0) {
            return hours + (hours == 1 ? " hour " : " hours ") + minutes + "m";
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        } else {
            return minutes + " minutes";
        }
    }

    // =====================================================
    // SPRINT TIMESHEET
    // =====================================================

    /**
     * Fetch current sprint issues + worklogs and build a timesheet.
     * Target: 7h/day out of 9h workday (as per lead).
     */
    public SprintTimesheetDto getSprintTimesheet(String assignee, String customStartDate, String customEndDate) {
        log.info("Fetching sprint timesheet for: {}, custom range: {} to {}", assignee, customStartDate, customEndDate);

        // 1. Query issues where the user has logged work in the current sprint
        String jql = "sprint in openSprints() AND worklogAuthor = \"" + assignee + "\" ORDER BY updated DESC";
        String fields = "summary,issuetype,status,priority,resolution,reporter,assignee," +
                "created,updated,parent,customfield_10020,customfield_10014,worklog";

        try {
            String response = getClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/2/search")
                            .queryParam("jql", jql)
                            .queryParam("fields", fields)
                            .queryParam("maxResults", 100)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode issuesNode = root.get("issues");

            // === PASS 1: Extract sprint metadata from the issues ===
            String sprintName = "";
            String sprintStartDate = "";
            String sprintEndDate = "";
            String sprintState = "";

            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    JsonNode issueFields = issueNode.get("fields");
                    if (sprintName.isEmpty() && issueFields.has("customfield_10020")
                            && issueFields.get("customfield_10020") != null
                            && issueFields.get("customfield_10020").isArray()
                            && issueFields.get("customfield_10020").size() > 0) {
                        // Pick the last (most recent/active) sprint
                        JsonNode sprintNode = issueFields.get("customfield_10020")
                                .get(issueFields.get("customfield_10020").size() - 1);
                        sprintName = textValue(sprintNode, "name");
                        sprintStartDate = textValue(sprintNode, "startDate");
                        sprintEndDate = textValue(sprintNode, "endDate");
                        sprintState = textValue(sprintNode, "state");
                        break;
                    }
                }
            }

            // Parse sprint date range for worklog filtering
            // Use custom dates if provided, otherwise fall back to sprint metadata
            LocalDate sprintStart = null;
            LocalDate sprintEnd = null;
            try {
                if (customStartDate != null && !customStartDate.isEmpty()) {
                    sprintStart = LocalDate.parse(customStartDate);
                    log.info("Using custom start date: {}", sprintStart);
                } else {
                    sprintStart = parseJiraDate(sprintStartDate);
                }
                if (customEndDate != null && !customEndDate.isEmpty()) {
                    sprintEnd = LocalDate.parse(customEndDate);
                    log.info("Using custom end date: {}", sprintEnd);
                } else {
                    sprintEnd = parseJiraDate(sprintEndDate);
                }
            } catch (Exception e) {
                log.warn("Could not parse sprint dates for filtering, using last 14 days");
                sprintStart = LocalDate.now().minusDays(13);
                sprintEnd = LocalDate.now();
            }

            // Override the display dates if custom dates were provided
            if (customStartDate != null && !customStartDate.isEmpty()) {
                sprintStartDate = customStartDate + "T00:00:00.000";
            }
            if (customEndDate != null && !customEndDate.isEmpty()) {
                sprintEndDate = customEndDate + "T23:59:59.999";
            }

            // === PASS 2: Fetch worklogs filtered by sprint date range ===
            List<SprintTimesheetDto.TimesheetEntry> entries = new ArrayList<>();
            Map<String, Map<String, Double>> dailyWorklogMap = new LinkedHashMap<>();

            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    JsonNode issueFields = issueNode.get("fields");
                    String issueKey = textValue(issueNode, "key");

                    // Fetch worklogs FILTERED by sprint date range
                    long totalLoggedSeconds = 0;
                    totalLoggedSeconds = fetchAndAccumulateWorklogs(
                            issueKey, assignee, dailyWorklogMap, totalLoggedSeconds,
                            sprintStart, sprintEnd);

                    String epicKey = "";
                    if (issueFields.has("customfield_10014") && issueFields.get("customfield_10014") != null
                            && !issueFields.get("customfield_10014").isNull()) {
                        epicKey = issueFields.get("customfield_10014").asText();
                    }

                    // Parent issue (for sub-tasks)
                    String parentKey = "";
                    String parentSummary = "";
                    if (issueFields.has("parent") && issueFields.get("parent") != null
                            && !issueFields.get("parent").isNull()) {
                        JsonNode parent = issueFields.get("parent");
                        parentKey = textValue(parent, "key");
                        if (parent.has("fields") && parent.get("fields").has("summary")) {
                            parentSummary = textValue(parent.get("fields"), "summary");
                        }
                    }

                    // Resolution
                    String resolution = "";
                    if (issueFields.has("resolution") && issueFields.get("resolution") != null
                            && !issueFields.get("resolution").isNull()) {
                        resolution = textValue(issueFields.get("resolution"), "name");
                    }

                    // Reporter & Assignee
                    String reporter = issueFields.has("reporter") && issueFields.get("reporter") != null
                            && issueFields.get("reporter").has("displayName")
                            ? issueFields.get("reporter").get("displayName").asText() : "";
                    String issueAssignee = issueFields.has("assignee") && issueFields.get("assignee") != null
                            && issueFields.get("assignee").has("displayName")
                            ? issueFields.get("assignee").get("displayName").asText() : "Unassigned";

                    double loggedHours = Math.round(totalLoggedSeconds / 36.0) / 100.0;

                    // Only include entries with actual work logged in this sprint
                    if (totalLoggedSeconds > 0) {
                        entries.add(SprintTimesheetDto.TimesheetEntry.builder()
                                .issueKey(issueKey)
                                .summary(textValue(issueFields, "summary"))
                                .issueType(issueFields.has("issuetype") && issueFields.get("issuetype").has("name")
                                        ? issueFields.get("issuetype").get("name").asText() : "Unknown")
                                .status(issueFields.has("status") && issueFields.get("status").has("name")
                                        ? issueFields.get("status").get("name").asText() : "Unknown")
                                .priority(issueFields.has("priority") && issueFields.get("priority") != null
                                        && issueFields.get("priority").has("name")
                                        ? issueFields.get("priority").get("name").asText() : "None")
                                .resolution(resolution)
                                .reporter(reporter)
                                .assignee(issueAssignee)
                                .created(textValue(issueFields, "created"))
                                .updated(textValue(issueFields, "updated"))
                                .parentKey(parentKey)
                                .parentSummary(parentSummary)
                                .loggedSeconds(totalLoggedSeconds)
                                .loggedHours(loggedHours)
                                .timeSpentDisplay(formatTimeSpent(totalLoggedSeconds))
                                .epicKey(epicKey)
                                .build());
                    }
                }
            }

            // Build daily breakdown from sprint start to sprint end (or today)
            List<SprintTimesheetDto.DailyLog> dailyBreakdown = buildDailyBreakdown(
                    sprintStartDate, sprintEndDate, dailyWorklogMap);

            double totalLoggedHours = entries.stream()
                    .mapToDouble(SprintTimesheetDto.TimesheetEntry::getLoggedHours).sum();

            return SprintTimesheetDto.builder()
                    .sprintName(sprintName)
                    .sprintStartDate(sprintStartDate)
                    .sprintEndDate(sprintEndDate)
                    .sprintState(sprintState)
                    .totalLoggedHours(Math.round(totalLoggedHours * 100.0) / 100.0)
                    .dailyTargetHours(7.0)
                    .workdayHours(9.0)
                    .entries(entries)
                    .dailyBreakdown(dailyBreakdown)
                    .build();

        } catch (WebClientResponseException e) {
            log.error("JIRA API error fetching sprint timesheet: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch sprint timesheet: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error building sprint timesheet", e);
            throw new RuntimeException("Error building sprint timesheet: " + e.getMessage());
        }
    }

    /**
     * Fetch worklogs for an issue and accumulate into daily map.
     * Only counts worklogs by the specified assignee that fall within the sprint date range.
     */
    private long fetchAndAccumulateWorklogs(String issueKey, String assignee,
                                             Map<String, Map<String, Double>> dailyMap,
                                             long existingSeconds,
                                             LocalDate sprintStart, LocalDate sprintEnd) {
        long total = existingSeconds;
        try {
            String response = getClient().get()
                    .uri("/rest/api/2/issue/" + issueKey + "/worklog")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode worklogs = root.get("worklogs");

            if (worklogs != null && worklogs.isArray()) {
                for (JsonNode wl : worklogs) {
                    // Filter by author — match on emailAddress or name (username)
                    JsonNode author = wl.get("author");
                    if (author == null) continue;

                    String authorEmail = author.has("emailAddress")
                            ? author.get("emailAddress").asText() : "";
                    String authorName = author.has("name")
                            ? author.get("name").asText() : "";
                    String authorDisplayName = author.has("displayName")
                            ? author.get("displayName").asText() : "";

                    boolean isMatch = assignee.equalsIgnoreCase(authorEmail)
                            || assignee.equalsIgnoreCase(authorName)
                            || assignee.equalsIgnoreCase(authorDisplayName);

                    if (!isMatch) continue;

                    // Filter by sprint date range
                    String started = textValue(wl, "started");
                    if (started.isEmpty() || started.length() < 10) continue;

                    String dateKey = started.substring(0, 10); // yyyy-MM-dd
                    LocalDate worklogDate;
                    try {
                        worklogDate = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (Exception e) {
                        continue; // skip unparseable dates
                    }

                    // Only count worklogs within the sprint date range
                    if (sprintStart != null && worklogDate.isBefore(sprintStart)) continue;
                    if (sprintEnd != null && worklogDate.isAfter(sprintEnd)) continue;

                    long timeSpent = wl.has("timeSpentSeconds")
                            ? wl.get("timeSpentSeconds").asLong() : 0;
                    total += timeSpent;

                    dailyMap.computeIfAbsent(dateKey, k -> new LinkedHashMap<>())
                            .merge(issueKey, (double) timeSpent / 3600.0, Double::sum);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch worklogs for {}: {}", issueKey, e.getMessage());
        }
        return total;
    }

    /**
     * Build a day-by-day breakdown from sprint start to end (or today).
     */
    private List<SprintTimesheetDto.DailyLog> buildDailyBreakdown(
            String startDateStr, String endDateStr,
            Map<String, Map<String, Double>> dailyWorklogMap) {

        List<SprintTimesheetDto.DailyLog> breakdown = new ArrayList<>();
        LocalDate today = LocalDate.now();

        LocalDate start;
        LocalDate end;

        try {
            // JIRA dates can be ISO format: 2024-01-15T10:00:00.000+05:30
            start = parseJiraDate(startDateStr);
            end = parseJiraDate(endDateStr);
        } catch (Exception e) {
            log.warn("Could not parse sprint dates, using last 14 days. start={}, end={}", startDateStr, endDateStr);
            start = today.minusDays(13);
            end = today;
        }

        // Clamp end to today if sprint is still active
        if (end.isAfter(today)) {
            end = today;
        }

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            DayOfWeek dow = date.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

            // Aggregate hours across all issues for this day
            Map<String, Double> issueHoursMap = dailyWorklogMap.getOrDefault(dateKey, Collections.emptyMap());
            double dayTotal = 0;

            // Create a rounded copy for output
            Map<String, Double> roundedMap = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : issueHoursMap.entrySet()) {
                double hours = Math.round(entry.getValue() * 100.0) / 100.0;
                roundedMap.put(entry.getKey(), hours);
                dayTotal += hours;
            }

            double target = isWeekend ? 0.0 : 7.0;
            dayTotal = Math.round(dayTotal * 100.0) / 100.0;

            breakdown.add(SprintTimesheetDto.DailyLog.builder()
                    .date(dateKey)
                    .dayOfWeek(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .loggedHours(dayTotal)
                    .targetHours(target)
                    .underTarget(!isWeekend && dayTotal < target)
                    .isToday(date.equals(today))
                    .isWeekend(isWeekend)
                    .issueBreakdown(roundedMap)
                    .build());
        }

        return breakdown;
    }

    /**
     * Parse JIRA date string to LocalDate.
     * Handles formats like: 2024-01-15, 2024-01-15T10:00:00.000+05:30
     */
    private LocalDate parseJiraDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IllegalArgumentException("Empty date string");
        }
        // Try ISO date-time first
        try {
            return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse date: " + dateStr);
        }
    }
}
