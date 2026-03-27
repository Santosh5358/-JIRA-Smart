import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { JiraApiService } from '../../services/jira-api.service';
import { ConnectionStatus } from '../../models/jira.models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  userInfo: { displayName: string; email: string; username: string; avatarUrl: string } | null = null;
  connectionStatus: ConnectionStatus | null = null;
  serverInfo: any = null;
  isLoading = false;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private jiraApi: JiraApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.userInfo = this.authService.getUserInfo();
    this.loadProfile();
  }

  loadProfile(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.jiraApi.checkConnection().subscribe({
      next: (status) => {
        this.connectionStatus = status;
        this.isLoading = false;

        // Parse serverInfo if available
        if ((status as any)?.serverInfo) {
          try {
            this.serverInfo = typeof (status as any).serverInfo === 'string'
              ? JSON.parse((status as any).serverInfo)
              : (status as any).serverInfo;
          } catch {
            this.serverInfo = null;
          }
        }
      },
      error: (err) => {
        this.errorMessage = 'Failed to load profile from JIRA.';
        this.isLoading = false;
      }
    });
  }

  getInitials(name: string | undefined): string {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    return parts[0].substring(0, 2).toUpperCase();
  }

  logout(): void {
    this.authService.logout();
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}
