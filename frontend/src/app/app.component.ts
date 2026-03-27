import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-layout" [class.no-sidebar]="!authService.isLoggedIn()">
      <!-- Sidebar (only when logged in) -->
      <aside class="sidebar" *ngIf="authService.isLoggedIn()">
        <div class="sidebar-header">
          <div class="logo-mark">
            <span class="material-icons-outlined">dashboard</span>
          </div>
          <div>
            <h1 class="logo-text">JIRA Smart</h1>
            <p class="logo-sub">Dashboard</p>
          </div>
        </div>

        <nav class="sidebar-nav">
          <span class="nav-group-label">Navigation</span>
          <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
            <span class="material-icons-outlined">view_list</span>
            <span>My Issues</span>
          </a>
          <a routerLink="/timesheet" routerLinkActive="active" class="nav-item">
            <span class="material-icons-outlined">schedule</span>
            <span>Sprint Timesheet</span>
          </a>

          <span class="nav-group-label" style="margin-top: 12px;">Quick Search</span>
          <div class="search-box">
            <span class="material-icons-outlined search-icon">search</span>
            <input
              type="text"
              class="search-input"
              placeholder="e.g. HCONN-32804"
              [(ngModel)]="searchKey"
              (keydown.enter)="searchIssue()"
            />
            <button class="search-go" *ngIf="searchKey.trim()" (click)="searchIssue()" title="Go">
              <span class="material-icons-outlined">arrow_forward</span>
            </button>
          </div>
        </nav>

        <!-- Profile link -->
        <a class="sidebar-profile" *ngIf="userInfo" routerLink="/profile" routerLinkActive="active">
          <div class="sp-avatar">
            <img *ngIf="userInfo.avatarUrl" [src]="userInfo.avatarUrl" alt="avatar" />
            <span *ngIf="!userInfo.avatarUrl" class="sp-initials">{{ getInitials(userInfo.displayName) }}</span>
            <span class="sp-dot"></span>
          </div>
          <span class="sp-name">{{ getFirstName(userInfo.displayName) }}</span>
          <span class="material-icons-outlined sp-chevron">chevron_right</span>
        </a>
      </aside>

      <!-- Main content -->
      <main class="main-content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .app-layout {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }

    .sidebar {
      width: 240px;
      min-width: 240px;
      background: #0f172a;
      color: var(--text-white);
      display: flex;
      flex-direction: column;
      padding: 0;
    }

    .sidebar-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 20px 18px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }

    .logo-mark {
      width: 34px;
      height: 34px;
      border-radius: 8px;
      background: linear-gradient(135deg, #4c9aff, #0052cc);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .material-icons-outlined {
        font-size: 20px;
        color: white;
      }
    }

    .logo-text {
      font-size: 16px;
      font-weight: 700;
      line-height: 1.2;
      letter-spacing: -0.3px;
    }

    .logo-sub {
      font-size: 11px;
      color: rgba(255,255,255,0.4);
      font-weight: 400;
    }

    .sidebar-nav {
      flex: 1;
      padding: 12px 10px;
    }

    .nav-group-label {
      display: block;
      font-size: 10px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: rgba(255,255,255,0.3);
      padding: 8px 12px 6px;
      margin-bottom: 2px;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 12px;
      border-radius: 6px;
      color: rgba(255,255,255,0.6);
      font-size: 13px;
      font-weight: 500;
      transition: all 0.15s ease;
      text-decoration: none;
      cursor: pointer;
      margin-bottom: 2px;

      &:hover {
        background: rgba(255,255,255,0.06);
        color: rgba(255,255,255,0.9);
        text-decoration: none;
      }

      &.active {
        background: rgba(76, 154, 255, 0.15);
        color: #4c9aff;
      }

      .material-icons-outlined { font-size: 19px; }
    }

    /* Sidebar Profile Link */
    .sidebar-profile {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      margin: 0 10px 10px;
      border-radius: 8px;
      border-top: 1px solid rgba(255,255,255,0.06);
      color: rgba(255,255,255,0.6);
      text-decoration: none;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background: rgba(255,255,255,0.06);
        color: rgba(255,255,255,0.9);
      }

      &.active {
        background: rgba(76, 154, 255, 0.12);
        color: #4c9aff;
      }
    }

    .sp-avatar {
      position: relative;
      flex-shrink: 0;

      img {
        width: 28px;
        height: 28px;
        border-radius: 8px;
        object-fit: cover;
      }
    }

    .sp-initials {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: 8px;
      background: linear-gradient(135deg, #4c9aff, #0052cc);
      color: white;
      font-size: 11px;
      font-weight: 700;
    }

    .sp-dot {
      position: absolute;
      bottom: -1px;
      right: -1px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #36b37e;
      border: 2px solid #0f172a;
    }

    .sp-name {
      font-size: 13px;
      font-weight: 500;
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .sp-chevron {
      font-size: 16px !important;
      opacity: 0.4;
    }

    .app-layout.no-sidebar {
      .main-content {
        width: 100%;
      }
    }

    .main-content {
      flex: 1;
      overflow-y: auto;
      background: #f8f9fb;
    }

    /* Search box */
    .search-box {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 4px 8px 0;
      padding: 6px 10px;
      border-radius: 8px;
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.08);
      transition: all 0.15s ease;

      &:focus-within {
        background: rgba(255,255,255,0.1);
        border-color: rgba(76, 154, 255, 0.4);
      }
    }

    .search-icon {
      font-size: 16px !important;
      color: rgba(255,255,255,0.35);
      flex-shrink: 0;
    }

    .search-input {
      flex: 1;
      background: transparent;
      border: none;
      outline: none;
      color: rgba(255,255,255,0.9);
      font-family: inherit;
      font-size: 12px;
      min-width: 0;

      &::placeholder {
        color: rgba(255,255,255,0.3);
      }
    }

    .search-go {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border: none;
      border-radius: 5px;
      background: rgba(76, 154, 255, 0.25);
      color: #4c9aff;
      cursor: pointer;
      flex-shrink: 0;
      transition: all 0.15s ease;

      .material-icons-outlined { font-size: 14px; }

      &:hover {
        background: rgba(76, 154, 255, 0.4);
      }
    }
  `]
})
export class AppComponent implements OnInit {
  userInfo: { displayName: string; email: string; username: string; avatarUrl: string } | null = null;
  searchKey = '';

  constructor(public authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.userInfo = this.authService.getUserInfo();
    // Re-check on login state changes
    this.authService.isLoggedIn$().subscribe(loggedIn => {
      this.userInfo = loggedIn ? this.authService.getUserInfo() : null;
    });
  }

  getInitials(name: string | undefined): string {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    return parts[0].substring(0, 2).toUpperCase();
  }

  getFirstName(name: string): string {
    return name?.split(' ')[0] || '';
  }

  searchIssue(): void {
    const key = this.searchKey.trim().toUpperCase();
    if (!key) return;
    this.searchKey = '';
    this.router.navigate(['/issue', key]);
  }
}
