import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { IssueAnalysisComponent } from './components/issue-analysis/issue-analysis.component';
import { LoginComponent } from './components/login/login.component';
import { ProfileComponent } from './components/profile/profile.component';
import { TimesheetComponent } from './components/timesheet/timesheet.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'timesheet', component: TimesheetComponent, canActivate: [authGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'issue/:issueKey', component: IssueAnalysisComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '/dashboard' }
];
