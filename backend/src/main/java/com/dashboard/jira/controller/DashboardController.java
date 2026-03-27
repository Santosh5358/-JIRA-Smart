package com.dashboard.jira.controller;

import com.dashboard.jira.dto.DashboardResponseDto;
import com.dashboard.jira.dto.SprintTimesheetDto;
import com.dashboard.jira.service.JiraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JiraService jiraService;

    /**
     * GET /api/dashboard/connection
     * Check if JIRA connection is healthy and return logged-in user info
     */
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> checkConnection() {
        log.info("Checking JIRA connection status");
        Map<String, Object> result = jiraService.checkConnection();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/dashboard/issues?assignee=john@company.com&status=In Progress&type=Story
     * Fetch all assigned issues for the dashboard view
     */
    @GetMapping("/issues")
    public ResponseEntity<DashboardResponseDto> getAssignedIssues(
            @RequestParam String assignee,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        log.info("Fetching issues for assignee: {}, status: {}, type: {}", assignee, status, type);
        DashboardResponseDto response = jiraService.getAssignedIssues(assignee, status, type);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/dashboard/sprint-timesheet?assignee=john@company.com
     * Fetch current sprint issues with worklogs for timesheet view.
     * Shows logged hours per task + daily breakdown with 7hr target highlight.
     */
    @GetMapping("/sprint-timesheet")
    public ResponseEntity<SprintTimesheetDto> getSprintTimesheet(
            @RequestParam String assignee,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("Fetching sprint timesheet for assignee: {}, startDate: {}, endDate: {}", assignee, startDate, endDate);
        SprintTimesheetDto response = jiraService.getSprintTimesheet(assignee, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
