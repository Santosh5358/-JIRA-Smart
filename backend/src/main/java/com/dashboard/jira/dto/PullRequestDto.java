package com.dashboard.jira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Holds data about a GitHub Pull Request and its code changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestDto {

    private String repoFullName;    // e.g., "dedalus-cis4u/hcis-connectivity"
    private int prNumber;
    private String title;
    private String state;           // open, closed, merged
    private String author;
    private String url;             // HTML URL for human viewing
    private String branch;          // head branch name
    private String targetBranch;    // base branch name
    private int additions;
    private int deletions;
    private int changedFiles;

    private List<FileChangeDto> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChangeDto {
        private String filename;
        private String status;      // added, removed, modified, renamed
        private int additions;
        private int deletions;
        private String patch;       // the actual diff/patch content
    }
}
