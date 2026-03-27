import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardResponse, AnalysisResponse, JiraIssue, ConnectionStatus, SprintTimesheet } from '../models/jira.models';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class JiraApiService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  /**
   * Check JIRA connection status — verifies backend can connect to JIRA
   */
  checkConnection(): Observable<ConnectionStatus> {
    return this.http.get<ConnectionStatus>(`${this.baseUrl}/dashboard/connection`);
  }

  /**
   * Fetch all issues assigned to a user
   */
  getAssignedIssues(assignee: string, status?: string, type?: string): Observable<DashboardResponse> {
    let params = new HttpParams().set('assignee', assignee);
    if (status) params = params.set('status', status);
    if (type) params = params.set('type', type);

    return this.http.get<DashboardResponse>(`${this.baseUrl}/dashboard/issues`, { params });
  }

  /**
   * Fetch full details for a single issue
   */
  getIssueDetails(issueKey: string): Observable<JiraIssue> {
    return this.http.get<JiraIssue>(`${this.baseUrl}/issues/${issueKey}`);
  }

  /**
   * Analyze an issue with AI - returns actionable guidance
   */
  analyzeIssue(issueKey: string): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(`${this.baseUrl}/issues/${issueKey}/analyze`);
  }

  /**
   * Get proxied attachment URL (images/files go through backend to add JIRA auth).
   * Includes JWT token as query param so native browser requests (img src, a href) work.
   */
  getAttachmentProxyUrl(contentUrl: string, mimeType: string): string {
    const token = localStorage.getItem('jira_jwt_token') || '';
    return `${this.baseUrl}/issues/attachment/proxy?url=${encodeURIComponent(contentUrl)}&mimeType=${encodeURIComponent(mimeType)}&token=${encodeURIComponent(token)}`;
  }

  /**
   * Fetch sprint timesheet — current sprint tasks with logged hours and daily breakdown
   */
  getSprintTimesheet(assignee: string, startDate?: string, endDate?: string): Observable<SprintTimesheet> {
    let params = new HttpParams().set('assignee', assignee);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<SprintTimesheet>(`${this.baseUrl}/dashboard/sprint-timesheet`, { params });
  }
}
