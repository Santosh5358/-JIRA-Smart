import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { JiraApiService } from '../../services/jira-api.service';
import { AuthService } from '../../services/auth.service';
import { SprintTimesheet, TimesheetEntry, DailyLog } from '../../models/jira.models';

@Component({
  selector: 'app-timesheet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './timesheet.component.html',
  styleUrl: './timesheet.component.scss'
})
export class TimesheetComponent implements OnInit {
  currentUser = '';
  currentDisplayName = '';

  timesheetData: SprintTimesheet | null = null;
  isLoading = false;
  errorMessage = '';
  timesheetView: 'daily' | 'tasks' = 'daily';

  // Date filter
  customStartDate = '';
  customEndDate = '';
  useCustomDates = false;

  constructor(
    private jiraApi: JiraApiService,
    private authService: AuthService,
    public router: Router
  ) {}

  ngOnInit(): void {
    const userInfo = this.authService.getUserInfo();
    if (userInfo) {
      this.currentUser = userInfo.username || userInfo.email || '';
      this.currentDisplayName = userInfo.displayName || '';
    }
    this.loadTimesheet();
  }

  loadTimesheet(): void {
    if (!this.currentUser.trim()) {
      this.errorMessage = 'Could not determine logged-in user.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

     // Auto-set end date to start date + 10 days if only start date is selected
    if (this.useCustomDates && this.customStartDate && !this.customEndDate) {
      const start = new Date(this.customStartDate);
      start.setDate(start.getDate() + 13);
      this.customEndDate = start.toISOString().split('T')[0];
    }

    this.jiraApi.getSprintTimesheet(
      this.currentUser,
      this.useCustomDates && this.customStartDate ? this.customStartDate : undefined,
      this.useCustomDates && this.customEndDate ? this.customEndDate : undefined
    ).subscribe({
      next: (data) => {
        this.timesheetData = data;
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Failed to load sprint timesheet.';
        this.isLoading = false;
      }
    });
  }

  getTimesheetTotalTarget(): number {
    if (!this.timesheetData) return 0;
    return this.timesheetData.dailyBreakdown
      .filter(d => !d.isWeekend)
      .reduce((sum, d) => sum + d.targetHours, 0);
  }

  getUnderTargetDays(): number {
    if (!this.timesheetData) return 0;
    return this.timesheetData.dailyBreakdown.filter(d => d.underTarget).length;
  }

  getDayBarWidth(day: DailyLog): number {
    if (day.isWeekend) return day.loggedHours > 0 ? Math.min((day.loggedHours / 7) * 100, 100) : 0;
    return Math.min((day.loggedHours / day.targetHours) * 100, 100);
  }

  getDayBarOverflow(day: DailyLog): number {
    if (day.targetHours <= 0) return 0;
    const overflow = day.loggedHours - day.targetHours;
    return overflow > 0 ? Math.min((overflow / day.targetHours) * 100, 50) : 0;
  }

  getTimesheetStatusClass(entry: TimesheetEntry): string {
    const s = entry.status.toLowerCase();
    if (s.includes('done') || s.includes('closed') || s.includes('resolved')) return 'ts-status-done';
    if (s.includes('hold') || s.includes('block')) return 'ts-status-blocked';
    if (s.includes('progress') || s.includes('ongoing')) return 'ts-status-progress';
    if (s.includes('review')) return 'ts-status-review';
    return 'ts-status-todo';
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

  formatHours(hours: number): string {
    if (hours === 0) return '0h';
    const h = Math.floor(hours);
    const m = Math.round((hours - h) * 60);
    if (m === 0) return `${h}h`;
    if (h === 0) return `${m}m`;
    return `${h}h ${m}m`;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  formatShortDate(dateStr: string): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length < 3) return dateStr;
    return `${parts[1]}/${parts[2]}`;
  }

  getIssueKeysForDay(day: DailyLog): string[] {
    return Object.keys(day.issueBreakdown || {});
  }

  getTotalLoggedPercent(): number {
    if (!this.timesheetData) return 0;
    const target = this.getTimesheetTotalTarget();
    if (target <= 0) return 0;
    return Math.round((this.timesheetData.totalLoggedHours / target) * 100);
  }
}
