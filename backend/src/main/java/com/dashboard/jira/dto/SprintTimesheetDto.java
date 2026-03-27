package com.dashboard.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SprintTimesheetDto {

    private String sprintName;
    private String sprintStartDate;
    private String sprintEndDate;
    private String sprintState;     // active, closed, future

    private double totalLoggedHours;
    private double dailyTargetHours;   // 7.0
    private double workdayHours;       // 9.0

    private List<TimesheetEntry> entries;
    private List<DailyLog> dailyBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimesheetEntry {
        private String issueKey;
        private String summary;
        private String issueType;
        private String status;
        private String priority;
        private String resolution;
        private String reporter;
        private String assignee;
        private String created;
        private String updated;
        private String parentKey;
        private String parentSummary;
        private double loggedHours;
        private long loggedSeconds;
        private String timeSpentDisplay;   // e.g. "6 hours", "1 hour"
        private String epicKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyLog {
        private String date;           // yyyy-MM-dd
        private String dayOfWeek;      // Mon, Tue, ...
        private double loggedHours;
        private double targetHours;    // 7.0
        private boolean underTarget;
        private boolean isToday;
        private boolean isWeekend;
        private Map<String, Double> issueBreakdown;  // issueKey -> hours
    }
}
