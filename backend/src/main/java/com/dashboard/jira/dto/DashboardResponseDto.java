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
public class DashboardResponseDto {

    private int totalIssues;
    private int stories;
    private int bugs;
    private int defects;
    private int tasks;
    private int others;
    private List<JiraIssueDto> issues;
}
