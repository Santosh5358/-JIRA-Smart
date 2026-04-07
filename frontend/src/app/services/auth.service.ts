import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

export interface LoginRequest {
  username: string;
  token: string;
  jiraBaseUrl?: string;
}

export interface LoginResponse {
  success: boolean;
  token?: string;
  displayName?: string;
  email?: string;
  username?: string;
  avatarUrl?: string;
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;
  private readonly TOKEN_KEY = 'jira_jwt_token';
  private readonly USER_KEY = 'jira_user_info';

  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  /**
   * Login with JIRA credentials
   */
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, credentials).pipe(
      tap(response => {
        if (response.success && response.token) {
          localStorage.setItem(this.TOKEN_KEY, response.token);
          localStorage.setItem(this.USER_KEY, JSON.stringify({
            displayName: response.displayName,
            email: response.email,
            username: response.username,
            avatarUrl: response.avatarUrl
          }));
          this.loggedIn$.next(true);
        }
      })
    );
  }

  /**
   * Logout — clear token and redirect to login
   */
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.loggedIn$.next(false);
    this.router.navigate(['/login']);
  }

  /**
   * Get the stored JWT token
   */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Check if user is logged in
   */
  isLoggedIn(): boolean {
    return this.hasToken();
  }

  /**
   * Observable for login state changes
   */
  isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  /**
   * Get stored user info
   */
  getUserInfo(): { displayName: string; email: string; username: string; avatarUrl: string } | null {
    const info = localStorage.getItem(this.USER_KEY);
    return info ? JSON.parse(info) : null;
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }
}
