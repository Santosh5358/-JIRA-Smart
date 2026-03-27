export interface JiraIssue {
  key: string;
  summary: string;
  description: string;
  issueType: string;
  status: string;
  priority: string;
  assignee: string;
  reporter: string;
  epicKey: string;
  epicName: string;
  sprintName: string;
  created: string;
  updated: string;
  projectName: string;
  projectKey: string;
  labels: string[];
  components: string[];
  attachments: Attachment[];
  comments: Comment[];
  linkedIssues: LinkedIssue[];
  subtasks: Subtask[];
  acceptanceCriteria: string;
}

export interface Attachment {
  filename: string;
  mimeType: string;
  contentUrl: string;
  thumbnailUrl: string;
  author: string;
  created: string;
  size: number;
}

export interface Comment {
  author: string;
  body: string;
  created: string;
}

export interface LinkedIssue {
  linkType: string;
  direction: string;
  relationshipDesc: string;
  issueKey: string;
  issueSummary: string;
  issueType: string;
  issueStatus: string;
  priority: string;
}

export interface Subtask {
  key: string;
  summary: string;
  status: string;
  issueType: string;
}

export interface ConnectionStatus {
  connected: boolean;
  displayName?: string;
  emailAddress?: string;
  username?: string;
  accountId?: string;
  avatarUrl?: string;
  timeZone?: string;
  locale?: string;
  active?: boolean;
  error?: string;
}

export interface DashboardResponse {
  totalIssues: number;
  stories: number;
  bugs: number;
  defects: number;
  tasks: number;
  others: number;
  issues: JiraIssue[];
}

export interface AnalysisResponse {
  issueKey: string;
  summary: string;
  issueType: string;
  // Developer-focused analysis
  tldr: string;
  implementationSteps: string;
  technicalAnalysis: string;
  filesAndComponents: string;
  edgeCasesAndTraps: string[];
  testCases: string[];
  codeReviewChecklist: string[];
  blockers: string;
  effort: string;
  quickRef: string;
  commentInsights: string;
  actionItems: string[];
  // PR code analysis
  prSummary: string;
  prCodeAnalysis: string;
  prSuggestions: string[];
  // Related context
  fullIssueDetails: JiraIssue;
  linkedIssueDetails: JiraIssue[];
  epicDetails: JiraIssue | null;
  pullRequests: PullRequest[];
}

export interface PullRequest {
  repoFullName: string;
  prNumber: number;
  title: string;
  state: string;
  author: string;
  url: string;
  branch: string;
  targetBranch: string;
  additions: number;
  deletions: number;
  changedFiles: number;
  files: PrFileChange[];
}

export interface PrFileChange {
  filename: string;
  status: string;
  additions: number;
  deletions: number;
  patch: string;
}

// ======= Sprint Timesheet =======

export interface SprintTimesheet {
  sprintName: string;
  sprintStartDate: string;
  sprintEndDate: string;
  sprintState: string;
  totalLoggedHours: number;
  dailyTargetHours: number;
  workdayHours: number;
  entries: TimesheetEntry[];
  dailyBreakdown: DailyLog[];
}

export interface TimesheetEntry {
  issueKey: string;
  summary: string;
  issueType: string;
  status: string;
  priority: string;
  resolution: string;
  reporter: string;
  assignee: string;
  created: string;
  updated: string;
  parentKey: string;
  parentSummary: string;
  loggedHours: number;
  loggedSeconds: number;
  timeSpentDisplay: string;
  epicKey: string;
}

export interface DailyLog {
  date: string;
  dayOfWeek: string;
  loggedHours: number;
  targetHours: number;
  underTarget: boolean;
  isToday: boolean;
  isWeekend: boolean;
  issueBreakdown: { [issueKey: string]: number };
}
