import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, LoginRequest } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  username = '';
  token = '';
  jiraBaseUrl = '';
  showAdvanced = false;
  isLoading = false;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    // If already logged in, redirect to dashboard
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  login(): void {
    if (!this.username.trim() || !this.token.trim()) {
      this.errorMessage = 'Please enter your JIRA username and API token.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const credentials: LoginRequest = {
      username: this.username.trim(),
      token: this.token.trim()
    };

    if (this.jiraBaseUrl.trim()) {
      credentials.jiraBaseUrl = this.jiraBaseUrl.trim();
    }

    this.authService.login(credentials).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.success) {
          this.router.navigate(['/dashboard']);
        } else {
          this.errorMessage = response.error || 'Login failed. Please check your credentials.';
        }
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid credentials. Check your username and API token.';
        } else if (err.status === 0) {
          this.errorMessage = 'Cannot reach the server. Make sure the backend is running.';
        } else {
          this.errorMessage = err.error?.error || 'Login failed. Please try again.';
        }
      }
    });
  }
}
