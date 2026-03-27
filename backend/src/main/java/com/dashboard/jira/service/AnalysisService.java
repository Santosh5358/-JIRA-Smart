package com.dashboard.jira.service;

import com.dashboard.jira.dto.AnalysisResponseDto;
import com.dashboard.jira.dto.JiraIssueDto;
import com.dashboard.jira.dto.PullRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AnalysisService {

    private final WebClient openAiWebClient;
    private final JiraService jiraService;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private int maxTokens;

    public AnalysisService(@Qualifier("openAiWebClient") WebClient openAiWebClient,
                           JiraService jiraService,
                           GitHubService gitHubService) {
        this.openAiWebClient = openAiWebClient;
        this.jiraService = jiraService;
        this.gitHubService = gitHubService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze a JIRA issue: fetches full context (linked issues, epic) and sends to AI
     */
    public AnalysisResponseDto analyzeIssue(String issueKey) {
        log.info("Starting analysis for issue: {}", issueKey);

        // 1. Fetch the main issue
        JiraIssueDto issue = jiraService.getIssueDetails(issueKey);

        // 2. Fetch linked issue details
        List<JiraIssueDto> linkedDetails = new ArrayList<>();
        if (issue.getLinkedIssues() != null) {
            for (JiraIssueDto.LinkDto link : issue.getLinkedIssues()) {
                try {
                    JiraIssueDto linked = jiraService.getIssueDetails(link.getIssueKey());
                    linkedDetails.add(linked);
                } catch (Exception e) {
                    log.warn("Could not fetch linked issue {}: {}", link.getIssueKey(), e.getMessage());
                }
            }
        }

        // 3. Fetch epic details if available
        JiraIssueDto epicDetails = null;
        if (issue.getEpicKey() != null && !issue.getEpicKey().isEmpty()) {
            try {
                epicDetails = jiraService.getEpicDetails(issue.getEpicKey());
            } catch (Exception e) {
                log.warn("Could not fetch epic {}: {}", issue.getEpicKey(), e.getMessage());
            }
        }

        // 4. Fetch GitHub PRs — from JIRA remote links + search by issue key
        List<PullRequestDto> pullRequests = fetchRelatedPullRequests(issueKey);

        // 5. Build the AI prompt (now includes PR diffs)
        String prompt = buildAnalysisPrompt(issue, linkedDetails, epicDetails, pullRequests);

        // 6. Call OpenAI
        String aiResponse = callOpenAi(prompt);

        // 7. Parse and build the response
        return buildAnalysisResponse(issueKey, issue, linkedDetails, epicDetails, pullRequests, aiResponse);
    }

    /**
     * Fetch PRs linked to this issue — from JIRA remote links AND by searching GitHub.
     */
    private List<PullRequestDto> fetchRelatedPullRequests(String issueKey) {
        Set<String> seenUrls = new HashSet<>();
        List<PullRequestDto> allPrs = new ArrayList<>();

        // Method 1: JIRA remote links (PR URLs attached to the issue)
        try {
            List<String> remoteUrls = jiraService.getGitHubPrUrls(issueKey);
            for (String url : remoteUrls) {
                if (seenUrls.add(url)) {
                    PullRequestDto pr = gitHubService.fetchPullRequest(url);
                    if (pr != null) allPrs.add(pr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch JIRA remote links for PRs: {}", e.getMessage());
        }

        // Method 2: Search GitHub for PRs mentioning this issue key
        try {
            List<PullRequestDto> searchResults = gitHubService.searchPrsByIssueKey(issueKey, null);
            for (PullRequestDto pr : searchResults) {
                if (seenUrls.add(pr.getUrl())) {
                    allPrs.add(pr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to search GitHub for PRs: {}", e.getMessage());
        }

        log.info("Total PRs found for {}: {}", issueKey, allPrs.size());
        return allPrs;
    }

    private String buildAnalysisPrompt(JiraIssueDto issue,
                                       List<JiraIssueDto> linkedIssues,
                                       JiraIssueDto epic,
                                       List<PullRequestDto> pullRequests) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a senior developer who has been on this team for years. A junior developer comes to you and says:\n");
        prompt.append("'I have this JIRA ticket assigned to me but I can't fully understand what I need to build. Can you help me understand it?'\n\n");
        prompt.append("Your job is to EXPLAIN the ticket like a mentor — clearly, thoroughly, with examples and pseudocode.\n");
        prompt.append("Think of how you'd explain it at a whiteboard to a teammate.\n\n");
        prompt.append("YOUR APPROACH:\n");
        prompt.append("1. START with the big picture: What is this about in simple terms? What problem is being solved? What is the developer's job here?\n");
        prompt.append("2. EXPLAIN key concepts/domain terms the developer needs to know (e.g., DNA = Did Not Attend, ADT = Admit-Discharge-Transfer)\n");
        prompt.append("3. DECODE the requirement: What EXACTLY triggers this? What should happen when triggered? What should NOT happen?\n");
        prompt.append("4. For technical artifacts (HL7 messages, JSON, SQL, API specs): PARSE THEM FIELD BY FIELD. Explain what each field means and which ones matter for the implementation\n");
        prompt.append("5. Give PSEUDOCODE showing the core logic — like writing it on a whiteboard\n");
        prompt.append("6. Clearly state what is NOT required (no UI changes? no DB changes? no new message types?)\n");
        prompt.append("7. READ COMMENTS CAREFULLY — comments often contain the REAL requirement, decisions, or solutions\n");
        prompt.append("8. If status is On Hold/Blocked/Closed, explain WHY and what was decided\n");
        prompt.append("9. Identify REGRESSION RISK — what existing functionality could break\n\n");
        prompt.append("WRITING STYLE:\n");
        prompt.append("- Use markdown formatting: **bold** for emphasis, `code` for technical terms, ```code blocks``` for pseudocode\n");
        prompt.append("- Be NARRATIVE and EXPLANATORY — don't just list, EXPLAIN why and how\n");
        prompt.append("- Use concrete examples with actual values from the ticket\n");
        prompt.append("- For each section, write enough that a developer who's never seen this codebase could understand\n\n");

        // Main issue
        prompt.append("=== MAIN ISSUE ===\n");
        prompt.append("Key: ").append(issue.getKey()).append("\n");
        prompt.append("Type: ").append(issue.getIssueType()).append("\n");
        prompt.append("Summary: ").append(issue.getSummary()).append("\n");
        prompt.append("Status: ").append(issue.getStatus()).append("\n");
        prompt.append("Priority: ").append(issue.getPriority()).append("\n");
        prompt.append("Project: ").append(issue.getProjectName()).append("\n");

        // Alert AI to status-specific behavior
        String status = issue.getStatus() != null ? issue.getStatus().toLowerCase() : "";
        if (status.contains("hold") || status.contains("block") || status.contains("waiting")) {
            prompt.append("⚠ STATUS ALERT: This issue is ON HOLD/BLOCKED. In your overview, explain WHY it is blocked (look at comments) and what needs to happen before work can resume.\n");
        }
        if (status.contains("closed") || status.contains("done") || status.contains("resolved")) {
            prompt.append("⚠ STATUS ALERT: This issue is ALREADY CLOSED. Summarize what was done and the resolution.\n");
        }

        if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
            prompt.append("Description:\n").append(issue.getDescription()).append("\n");
        }

        if (issue.getAcceptanceCriteria() != null && !issue.getAcceptanceCriteria().isEmpty()) {
            prompt.append("Acceptance Criteria:\n").append(issue.getAcceptanceCriteria()).append("\n");
        }

        if (issue.getLabels() != null && !issue.getLabels().isEmpty()) {
            prompt.append("Labels: ").append(String.join(", ", issue.getLabels())).append("\n");
        }

        if (issue.getComponents() != null && !issue.getComponents().isEmpty()) {
            prompt.append("Components: ").append(String.join(", ", issue.getComponents())).append("\n");
        }

        // Comments — CRITICAL: these often contain the actual solution or key decisions
        if (issue.getComments() != null && !issue.getComments().isEmpty()) {
            prompt.append("\n--- Comments (READ THESE CAREFULLY — they often contain the solution or critical context) ---\n");
            for (JiraIssueDto.CommentDto comment : issue.getComments()) {
                prompt.append("[").append(comment.getAuthor()).append(" on ")
                        .append(comment.getCreated()).append("]: ")
                        .append(comment.getBody()).append("\n");
            }
        }

        // Attachments — download text-based content for AI context
        if (issue.getAttachments() != null && !issue.getAttachments().isEmpty()) {
            prompt.append("\n--- Attachments ---\n");

            // First, list all attachments
            for (JiraIssueDto.AttachmentDto att : issue.getAttachments()) {
                prompt.append("- ").append(att.getFilename())
                        .append(" (").append(att.getMimeType())
                        .append(", ").append(formatFileSize(att.getSize())).append(")\n");
            }

            // Then download and include text-based attachment content
            for (JiraIssueDto.AttachmentDto att : issue.getAttachments()) {
                try {
                    String content = jiraService.downloadTextAttachment(
                            att.getContentUrl(), att.getMimeType(), att.getSize());
                    if (content != null && !content.isBlank()) {
                        prompt.append("\n📄 FILE CONTENT: ").append(att.getFilename()).append("\n");
                        prompt.append("```\n");
                        prompt.append(truncate(content, 5000));
                        prompt.append("\n```\n");
                    }
                } catch (Exception e) {
                    log.warn("Failed to download attachment {} for AI: {}", att.getFilename(), e.getMessage());
                }
            }

            // For image attachments, tell the AI they exist
            boolean hasImages = issue.getAttachments().stream()
                    .anyMatch(a -> a.getMimeType() != null && a.getMimeType().startsWith("image/"));
            if (hasImages) {
                prompt.append("\n⚠ IMAGE ATTACHMENTS: This issue has image attachments (screenshots/diagrams). ");
                prompt.append("They may contain UI mockups, error screenshots, architecture diagrams, or workflow diagrams. ");
                prompt.append("Mention their existence when relevant and suggest the developer review them.\n");
            }
        }

        // Subtasks
        if (issue.getSubtasks() != null && !issue.getSubtasks().isEmpty()) {
            prompt.append("\n--- Subtasks ---\n");
            for (JiraIssueDto.SubtaskDto st : issue.getSubtasks()) {
                prompt.append("- ").append(st.getKey()).append(": ")
                        .append(st.getSummary()).append(" [").append(st.getStatus()).append("]\n");
            }
        }

        // Epic context
        if (epic != null) {
            prompt.append("\n=== PARENT EPIC ===\n");
            prompt.append("Key: ").append(epic.getKey()).append("\n");
            prompt.append("Summary: ").append(epic.getSummary()).append("\n");
            if (epic.getDescription() != null && !epic.getDescription().isEmpty()) {
                prompt.append("Epic Description:\n").append(epic.getDescription()).append("\n");
            }
        }

        // Linked issues
        if (!linkedIssues.isEmpty()) {
            prompt.append("\n=== LINKED ISSUES ===\n");
            for (JiraIssueDto linked : linkedIssues) {
                prompt.append("- ").append(linked.getKey()).append(" [")
                        .append(linked.getIssueType()).append("] ")
                        .append(linked.getSummary()).append(" (Status: ")
                        .append(linked.getStatus()).append(")\n");
                if (linked.getDescription() != null && !linked.getDescription().isEmpty()) {
                    prompt.append("  Description: ").append(truncate(linked.getDescription(), 300)).append("\n");
                }
            }
        }

        // === GITHUB PULL REQUESTS (CODE CHANGES) ===
        if (pullRequests != null && !pullRequests.isEmpty()) {
            prompt.append("\n=== GITHUB PULL REQUESTS (CODE CHANGES) ===\n");
            prompt.append("⚡ IMPORTANT: These are ACTUAL CODE CHANGES (PRs) linked to this issue.\n");
            prompt.append("Read the diffs carefully to understand:\n");
            prompt.append("  - What was ACTUALLY implemented (vs what the ticket says)\n");
            prompt.append("  - Which files/classes were changed\n");
            prompt.append("  - What the developer needs to continue, review, or fix\n\n");

            for (PullRequestDto pr : pullRequests) {
                prompt.append("--- PR #").append(pr.getPrNumber()).append(": ")
                        .append(pr.getTitle()).append(" ---\n");
                prompt.append("State: ").append(pr.getState())
                        .append(" | Author: ").append(pr.getAuthor())
                        .append(" | Branch: ").append(pr.getBranch())
                        .append(" → ").append(pr.getTargetBranch()).append("\n");
                prompt.append("Files changed: ").append(pr.getChangedFiles())
                        .append(" | +").append(pr.getAdditions())
                        .append(" -").append(pr.getDeletions()).append("\n");
                prompt.append("URL: ").append(pr.getUrl()).append("\n\n");

                if (pr.getFiles() != null) {
                    prompt.append("Changed files:\n");
                    for (PullRequestDto.FileChangeDto file : pr.getFiles()) {
                        prompt.append("  ").append(file.getStatus().toUpperCase())
                                .append(": ").append(file.getFilename())
                                .append(" (+").append(file.getAdditions())
                                .append(" -").append(file.getDeletions()).append(")\n");
                        if (file.getPatch() != null && !file.getPatch().isEmpty()) {
                            prompt.append("  DIFF:\n").append(file.getPatch()).append("\n\n");
                        }
                    }
                }
                prompt.append("\n");
            }
        }

        prompt.append("\n=== INSTRUCTIONS ===\n");
        prompt.append("The developer has read this ticket but needs YOUR HELP to understand what to build.\n");
        prompt.append("Explain it like a mentor at a whiteboard — clear, detailed, with examples.\n");
        prompt.append("DO NOT just rephrase the description. DECODE it: what changes in the system, what's the before vs after, what's missing from the ticket.\n");
        prompt.append("If it's a STORY: explain the trigger, the action, the expected outcome, and what should NOT happen.\n");
        prompt.append("If it's a BUG/DEFECT: explain what's broken, the root cause theory, and what the fix should look like.\n");
        prompt.append("If it's a TASK: explain what needs to be done and the expected outcome.\n");
        prompt.append("IMPORTANT: Use markdown formatting in ALL string values — **bold**, `code`, ```pseudocode blocks```, bullet points, numbered lists.\n\n");

        // PR-specific instructions
        if (pullRequests != null && !pullRequests.isEmpty()) {
            prompt.append("=== CODE REVIEW INSTRUCTIONS ===\n");
            prompt.append("GitHub PRs are attached above with ACTUAL CODE DIFFS. You MUST:\n");
            prompt.append("1. In 'prSummary': Summarize what EACH PR does in plain English — what files changed, what logic was added/modified/removed\n");
            prompt.append("2. In 'prCodeAnalysis': Compare the PR code against the JIRA requirement. Does the code FULLY implement the requirement? What's missing? What looks wrong? Are there any bugs?\n");
            prompt.append("3. In 'prSuggestions': Tell the developer EXACTLY what they still need to do — remaining work, tests to add, things to fix in the PR, things to watch in code review\n");
            prompt.append("4. Use the PR diffs to BETTER understand the requirement — the code often reveals what the ticket really means\n\n");
        }

        prompt.append("Respond in the following JSON format (no markdown code fencing around the JSON itself, just raw JSON):\n");
        prompt.append("{\n");
        prompt.append("  \"tldr\": \"START with 'In simple terms:' and explain what this ticket is about in 3-5 sentences a developer can immediately understand. Cover: (1) What is the problem/feature? (2) What triggers it? (3) What should happen? (4) What should NOT happen? End with a one-line summary. Use **bold** for key terms.\",\n");
        prompt.append("  \"implementationSteps\": \"Detailed step-by-step implementation plan (numbered). Each step should explain WHAT to do and WHY. Include ```pseudocode``` blocks showing the core logic — like you're writing it on a whiteboard. Example: '1. **Locate the ADT^A08 handler** — find the class that processes incoming ADT messages...\\n2. **Add condition for EVN.4** — check the event code...\\n```\\nif (evn4 == \\\"OAS-DN\\\") {\\n    cancelEncounter(encounterId);\\n    setAppointmentStatus(\\\"NoShow\\\");\\n}\\n```'. Make each step actionable with specific class/method names where possible.\",\n");
        prompt.append("  \"technicalAnalysis\": \"DEEP DIVE into technical artifacts in the ticket. For HL7 messages: parse EACH segment and field, explain what it means. For JSON/SQL/APIs: explain the data structure. For each artifact, create a **Field Breakdown** showing: field name → value → what it means for code. Example: '**EVN Segment Breakdown:**\\n- `EVN.4` = `OAS-DN` → This is the trigger event code. OAS-DN means No-Show/DNA\\n- **PV1 Key Fields:**\\n- `PV1.19` = `ENC0009803` → This is the encounter ID you need to cancel'. Include data flow: where data comes from, how it flows through the system, where it ends up.\",\n");
        prompt.append("  \"filesAndComponents\": \"Which files, classes, services, or modules need to change? Be specific. Group by layer (Handler/Controller, Service/Logic, Repository/DAO). Also clearly state what does NOT need to change (e.g., 'No UI changes needed', 'No database schema changes'). If PRs are present, reference the actual files from the diff.\",\n");
        prompt.append("  \"edgeCasesAndTraps\": [\"FORMAT EACH ITEM AS: 'SCENARIO: [what can go wrong — be specific with actual values] → IMPACT: [what will break/happen if not handled]'. Cover: null/empty inputs, race conditions, already-processed states, missing data, encoding issues, duplicate messages, backwards compatibility. Be SPECIFIC to this ticket — not generic boilerplate.\"],\n");
        prompt.append("  \"testCases\": [\"FORMAT EACH ITEM AS: 'TEST: [action with specific input values from the ticket] → EXPECT: [exact expected outcome with field values]'. Include: (1) Happy path with actual sample data, (2) The blocking/negative case, (3) Edge cases (missing fields, already cancelled, duplicate). Use real values from the sample message in the ticket.\"],\n");
        prompt.append("  \"codeReviewChecklist\": [\"Specific things to verify before raising/merging PR. Include: logging added, error handling, idempotency, regression impact, configuration changes.\"],\n");
        prompt.append("  \"blockers\": \"What's blocking or unclear? What questions should the developer ask before starting? Write 'NONE' if everything is clear.\",\n");
        prompt.append("  \"effort\": \"Time estimate: **Implementation** Xh | **Tests** Xh | **Code Review** Xh | **Total** Xh. Briefly justify each.\",\n");
        prompt.append("  \"quickRef\": \"Explain ALL domain terms, acronyms, and protocols. Format each as: **TERM** — explanation. Example: '**DNA** — Did Not Attend. Patient had an appointment but didn't show up. **ADT^A08** — HL7 message type for patient information update.'\",\n");
        prompt.append("  \"commentInsights\": \"KEY info from comments: decisions made, solutions proposed, questions answered, scope changes. Say 'No actionable comments' if none.\",\n");
        prompt.append("  \"actionItems\": [\"Final checklist of concrete tasks the developer should do, in order. Each item = one action.\"],\n");

        // PR-specific fields (always included in schema, may be empty)
        prompt.append("  \"prSummary\": \"");
        if (pullRequests != null && !pullRequests.isEmpty()) {
            prompt.append("Summary of ALL attached PRs: what each PR does, what files were changed, what's the overall code change. Be specific — reference actual class names, method names, and logic from the diffs.");
        } else {
            prompt.append("No PRs found for this issue.");
        }
        prompt.append("\",\n");
        prompt.append("  \"prCodeAnalysis\": \"");
        if (pullRequests != null && !pullRequests.isEmpty()) {
            prompt.append("DETAILED analysis: Does the PR code match the JIRA requirement? What's implemented correctly? What's missing? Any bugs, bad patterns, or missing error handling you can spot in the diffs?");
        } else {
            prompt.append("No PRs to analyze.");
        }
        prompt.append("\",\n");
        prompt.append("  \"prSuggestions\": ");
        if (pullRequests != null && !pullRequests.isEmpty()) {
            prompt.append("[\"What the developer should still do: remaining implementation, tests to add, things to fix in the PR, code review notes. Each item = one action.\"]");
        } else {
            prompt.append("[]");
        }
        prompt.append("\n");

        prompt.append("}\n");

        return prompt.toString();
    }

    private String callOpenAi(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", 0.3);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content", "You are a senior developer and mentor who has been on this team for years. " +
                            "A developer comes to you with a JIRA ticket they don't fully understand and asks for help. " +
                            "Your job is to explain it like you're at a whiteboard with them — clear, thorough, with examples. " +
                            "RULES: " +
                            "(1) Start with the big picture — what is this about in simple terms? " +
                            "(2) Explain key domain concepts the developer needs to know. " +
                            "(3) DECODE the requirement into concrete developer terms — what triggers this, what should happen, what should NOT happen. " +
                            "(4) Parse technical artifacts (HL7 messages, JSON, SQL) FIELD BY FIELD — explain each relevant field. " +
                            "(5) Give pseudocode showing the core logic. " +
                            "(6) Identify what is NOT required (scope boundaries). " +
                            "(7) Read comments carefully — they often contain the REAL requirement or solution. " +
                            "(8) If GitHub PR diffs are provided, analyze the code changes deeply. " +
                            "Use markdown formatting in all string values: **bold**, `code`, ```code blocks```, - bullet points. " +
                            "Be NARRATIVE and EXPLANATORY — don't just list things, EXPLAIN them. " +
                            "Respond in valid JSON only — no markdown code fencing around the JSON."
            ));
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);

            log.info("Calling OpenAI ({}) with prompt length {} chars", model, prompt.length());

            String response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            log.info("OpenAI response received ({} chars)", response != null ? response.length() : 0);

            JsonNode root = objectMapper.readTree(response);
            return root.get("choices").get(0).get("message").get("content").asText();

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage(), e);
            return null;  // Signal fallback to buildAnalysisResponse
        }
    }

    private AnalysisResponseDto buildAnalysisResponse(String issueKey,
                                                       JiraIssueDto issue,
                                                       List<JiraIssueDto> linkedDetails,
                                                       JiraIssueDto epicDetails,
                                                       List<PullRequestDto> pullRequests,
                                                       String aiResponse) {
        AnalysisResponseDto.AnalysisResponseDtoBuilder builder = AnalysisResponseDto.builder()
                .issueKey(issueKey)
                .summary(issue.getSummary())
                .issueType(issue.getIssueType())
                .fullIssueDetails(issue)
                .linkedIssueDetails(linkedDetails)
                .epicDetails(epicDetails)
                .pullRequests(pullRequests);

        // If AI call failed, return a clean fallback directly (no JSON parsing)
        if (aiResponse == null) {
            return buildFallbackResponse(builder);
        }

        try {
            // Clean potential markdown code fencing from response
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode analysis = objectMapper.readTree(cleaned);

            builder.tldr(jsonText(analysis, "tldr"));

            // implementationSteps may be empty if GPT ran out of tokens — auto-generate from actionItems
            String implSteps = jsonText(analysis, "implementationSteps");
            if (implSteps.isBlank() && analysis.has("actionItems") && analysis.get("actionItems").isArray()) {
                StringBuilder sb = new StringBuilder();
                int step = 1;
                for (JsonNode item : analysis.get("actionItems")) {
                    sb.append(step++).append(". ").append(item.asText()).append("\n");
                }
                implSteps = sb.toString().trim();
            }
            builder.implementationSteps(implSteps);

            builder.technicalAnalysis(jsonText(analysis, "technicalAnalysis"));
            builder.filesAndComponents(jsonText(analysis, "filesAndComponents"));
            builder.blockers(jsonText(analysis, "blockers"));
            builder.effort(jsonText(analysis, "effort"));
            builder.quickRef(jsonText(analysis, "quickRef"));
            builder.commentInsights(jsonText(analysis, "commentInsights"));

            builder.edgeCasesAndTraps(jsonArrayToList(analysis, "edgeCasesAndTraps"));
            builder.testCases(jsonArrayToList(analysis, "testCases"));
            builder.codeReviewChecklist(jsonArrayToList(analysis, "codeReviewChecklist"));
            builder.actionItems(jsonArrayToList(analysis, "actionItems"));

            // PR analysis fields
            builder.prSummary(jsonText(analysis, "prSummary"));
            builder.prCodeAnalysis(jsonText(analysis, "prCodeAnalysis"));
            builder.prSuggestions(jsonArrayToList(analysis, "prSuggestions"));

        } catch (Exception e) {
            log.warn("Could not parse AI response as JSON, using fallback", e);
            return buildFallbackResponse(builder);
        }

        return builder.build();
    }

    /**
     * Build a clean fallback response when AI is unreachable or response can't be parsed.
     * Only sets TL;DR with a clear message — all other sections are left empty so they hide in the UI.
     */
    private AnalysisResponseDto buildFallbackResponse(AnalysisResponseDto.AnalysisResponseDtoBuilder builder) {
        builder.tldr("⚠ AI service is currently unreachable. This could be due to a missing or invalid OpenAI API key, network issues, or the AI service being down. Please read the ticket description and comments manually.")
               .implementationSteps("")
               .technicalAnalysis("")
               .filesAndComponents("")
               .edgeCasesAndTraps(List.of())
               .testCases(List.of())
               .codeReviewChecklist(List.of())
               .blockers("")
               .effort("")
               .quickRef("")
               .commentInsights("")
               .actionItems(List.of())
               .prSummary("")
               .prCodeAnalysis("")
               .prSuggestions(List.of());

        return builder.build();
    }

    private String jsonText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    private List<String> jsonArrayToList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node.has(field) && node.get(field).isArray()) {
            for (JsonNode item : node.get(field)) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
