package com.dashboard.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDto {

    private String key;
    private String summary;
    private String description;
    private String issueType;      // Story, Bug, Task, etc.
    private String status;
    private String priority;
    private String assignee;
    private String reporter;
    private String epicKey;
    private String epicName;
    private String sprintName;
    private String created;
    private String updated;
    private String projectName;
    private String projectKey;

    private List<String> labels;
    private List<String> components;
    private List<AttachmentDto> attachments;
    private List<CommentDto> comments;
    private List<LinkDto> linkedIssues;
    private List<SubtaskDto> subtasks;
    private String acceptanceCriteria;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachmentDto {
        private String filename;
        private String mimeType;
        private String contentUrl;
        private String thumbnailUrl;
        private String author;
        private String created;
        private long size;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentDto {
        private String author;
        private String body;
        private String created;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkDto {
        private String linkType;
        private String direction;   // inward or outward
        private String relationshipDesc;  // e.g. "is blocked by", "blocks", "relates to"
        private String issueKey;
        private String issueSummary;
        private String issueType;
        private String issueStatus;
        private String priority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubtaskDto {
        private String key;
        private String summary;
        private String status;
        private String issueType;
    }
}
