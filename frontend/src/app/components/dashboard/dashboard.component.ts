import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { JiraApiService } from '../../services/jira-api.service';
import { AuthService } from '../../services/auth.service';
import { DashboardResponse, JiraIssue, ConnectionStatus } from '../../models/jira.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  statusFilter = '';
  typeFilter = '';

  dashboardData: DashboardResponse | null = null;
  filteredIssues: JiraIssue[] = [];
  focusIssues: JiraIssue[] = [];
  blockedIssues: JiraIssue[] = [];
  isLoading = false;
  errorMessage = '';
  searchText = '';
  hideClosedSubtasks = true;

  // Logged-in user identity (resolved from auth)
  currentUser = '';
  currentDisplayName = '';

  // JIRA connection status
  connectionStatus: ConnectionStatus | null = null;
  isCheckingConnection = false;

  statusOptions = ['', 'To Do', 'In Progress', 'In Review', 'Done', 'Closed', 'Implementation - Ongoing', 'Analysis - on hold', 'Implementation - On Hold'];
  typeOptions = ['', 'Story', 'Bug', 'Defect', 'Task', 'Sub-task', 'Epic'];

  // Computed stats for active issues only
  activeStats = { total: 0, stories: 0, defects: 0, tasks: 0, blocked: 0 };

  constructor(
    private jiraApi: JiraApiService,
    private authService: AuthService,
    public router: Router
  ) {}

  ngOnInit(): void {
    // Get logged-in user identity from auth service
    const userInfo = this.authService.getUserInfo();
    if (userInfo) {
      this.currentUser = userInfo.username || userInfo.email || '';
      this.currentDisplayName = userInfo.displayName || '';
    }

    // Check JIRA connection and auto-load issues
    this.checkJiraConnection();
  }

  checkJiraConnection(): void {
    this.isCheckingConnection = true;
    this.jiraApi.checkConnection().subscribe({
      next: (status) => {
        this.connectionStatus = status;
        this.isCheckingConnection = false;

        // Use username from JIRA /myself as the identity
        if (status.connected) {
          this.currentUser = status.username || status.emailAddress || this.currentUser;
          this.currentDisplayName = status.displayName || this.currentDisplayName;
          this.loadIssues();
        }
      },
      error: () => {
        this.connectionStatus = { connected: false, error: 'Backend server is not reachable' };
        this.isCheckingConnection = false;
        // Still try to load with cached user if available
        if (this.currentUser) {
          this.loadIssues();
        }
      }
    });
  }

  loadIssues(): void {
    if (!this.currentUser.trim()) {
      this.errorMessage = 'Could not determine logged-in user.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.jiraApi.getAssignedIssues(
      this.currentUser,
      this.statusFilter || undefined,
      this.typeFilter || undefined
    ).subscribe({
      next: (data) => {
        this.dashboardData = data;
        this.categorizeAndSort(data.issues);
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Failed to fetch issues. Check your credentials and try again.';
        this.isLoading = false;
      }
    });
  }

  /** Smart categorization: focus, blocked, and filtered list */
  private categorizeAndSort(issues: JiraIssue[]): void {
    // Separate active vs closed/done
    const active: JiraIssue[] = [];
    const closed: JiraIssue[] = [];

    for (const issue of issues) {
      if (this.isClosedOrDone(issue)) {
        closed.push(issue);
      } else {
        active.push(issue);
      }
    }

    // Sort by priority: Emergency > Highest > High > Medium > Low > Lowest
    active.sort((a, b) => this.priorityWeight(a.priority) - this.priorityWeight(b.priority));

    // Focus: active non-subtask items that need attention NOW
    this.focusIssues = active.filter(i =>
      !this.isSubtask(i) && !this.isBlocked(i)
    );

    // Blocked: on hold / blocked items
    this.blockedIssues = active.filter(i => this.isBlocked(i) && !this.isSubtask(i));

    // Compute active stats
    this.activeStats = {
      total: active.filter(i => !this.isSubtask(i)).length,
      stories: active.filter(i => i.issueType.toLowerCase() === 'story').length,
      defects: active.filter(i => ['defect', 'bug'].includes(i.issueType.toLowerCase())).length,
      tasks: active.filter(i => i.issueType.toLowerCase() === 'task').length,
      blocked: this.blockedIssues.length
    };

    // Default: show active items, hide closed subtasks
    this.applyFilters(active, closed);
  }

  private applyFilters(active: JiraIssue[], closed: JiraIssue[]): void {
    let issues = this.hideClosedSubtasks
      ? active
      : [...active, ...closed];

    if (this.searchText.trim()) {
      const search = this.searchText.toLowerCase();
      issues = issues.filter(i =>
        i.key.toLowerCase().includes(search) ||
        i.summary.toLowerCase().includes(search) ||
        i.issueType.toLowerCase().includes(search) ||
        i.status.toLowerCase().includes(search)
      );
    }

    this.filteredIssues = issues;
  }

  filterIssues(): void {
    if (!this.dashboardData) return;
    this.categorizeAndSort(this.dashboardData.issues);
  }

  toggleClosedSubtasks(): void {
    this.hideClosedSubtasks = !this.hideClosedSubtasks;
    if (this.dashboardData) {
      this.categorizeAndSort(this.dashboardData.issues);
    }
  }

  openAnalysis(issue: JiraIssue): void {
    this.router.navigate(['/issue', issue.key]);
  }

  logout(): void {
    this.authService.logout();
  }

  // === Helpers ===

  isClosedOrDone(issue: JiraIssue): boolean {
    const s = (issue.status || '').toLowerCase();
    return s.includes('closed') || s.includes('done') || s.includes('resolved');
  }

  isBlocked(issue: JiraIssue): boolean {
    const s = (issue.status || '').toLowerCase();
    return s.includes('hold') || s.includes('block') || s.includes('waiting');
  }

  isSubtask(issue: JiraIssue): boolean {
    return issue.issueType.toLowerCase() === 'sub-task';
  }

  isEmergency(issue: JiraIssue): boolean {
    const p = (issue.priority || '').toLowerCase();
    return p === 'emergency' || p === 'highest' || p === 'blocker';
  }

  hasMissingAC(issue: JiraIssue): boolean {
    const type = issue.issueType.toLowerCase();
    return (type === 'story' || type === 'bug' || type === 'defect') &&
           (!issue.acceptanceCriteria || issue.acceptanceCriteria.trim() === '');
  }

  hasNoDescription(issue: JiraIssue): boolean {
    return !issue.description || issue.description.trim() === '';
  }

  hasComments(issue: JiraIssue): boolean {
    return issue.comments != null && issue.comments.length > 0;
  }

  private priorityWeight(priority: string): number {
    switch ((priority || '').toLowerCase()) {
      case 'emergency': case 'blocker': return 0;
      case 'highest': return 1;
      case 'high': return 2;
      case 'medium': return 3;
      case 'low': return 4;
      case 'lowest': return 5;
      default: return 6;
    }
  }

  getTypeBadgeClass(type: string): string {
    switch (type.toLowerCase()) {
      case 'story': return 'badge-story';
      case 'bug': case 'defect': return 'badge-bug';
      case 'task': return 'badge-task';
      case 'epic': return 'badge-epic';
      case 'sub-task': return 'badge-subtask';
      default: return 'badge-default';
    }
  }

  getStatusBadgeClass(status: string): string {
    const s = status.toLowerCase();
    if (s.includes('done') || s.includes('closed') || s.includes('resolved')) return 'badge-status-done';
    if (s.includes('hold') || s.includes('block')) return 'badge-status-blocked';
    if (s.includes('progress') || s.includes('ongoing')) return 'badge-status-progress';
    if (s.includes('review')) return 'badge-status-review';
    return 'badge-status-todo';
  }

  getPriorityBadgeClass(priority: string): string {
    switch (priority.toLowerCase()) {
      case 'emergency': case 'blocker': return 'badge-priority-emergency';
      case 'highest': return 'badge-priority-highest';
      case 'high': return 'badge-priority-high';
      case 'medium': return 'badge-priority-medium';
      case 'low': return 'badge-priority-low';
      case 'lowest': return 'badge-priority-lowest';
      default: return 'badge-default';
    }
  }

  getTypeIcon(type: string): string {
    switch (type.toLowerCase()) {
      case 'story': return 'auto_stories';
      case 'bug': case 'defect': return 'bug_report';
      case 'task': return 'task_alt';
      case 'epic': return 'bolt';
      case 'sub-task': return 'subdirectory_arrow_right';
      default: return 'article';
    }
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

}
