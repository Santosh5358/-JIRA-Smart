package com.dashboard.jira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {

    private String issueKey;
    private String summary;
    private String issueType;

    // AI-generated analysis — developer focused
    private String tldr;
    private String implementationSteps;
    private String technicalAnalysis;
    private String filesAndComponents;
    private List<String> edgeCasesAndTraps;
    private List<String> testCases;
    private List<String> codeReviewChecklist;
    private String blockers;
    private String effort;
    private String quickRef;
    private String commentInsights;
    private List<String> actionItems;

    // PR code review — AI reads the diff and tells you what's happening
    private String prSummary;              // AI summary of what PRs do
    private String prCodeAnalysis;         // AI analysis of the code changes vs requirements
    private List<String> prSuggestions;    // what the dev should do based on PRs

    // Related context
    private JiraIssueDto fullIssueDetails;
    private List<JiraIssueDto> linkedIssueDetails;
    private JiraIssueDto epicDetails;
    private List<PullRequestDto> pullRequests;
}
