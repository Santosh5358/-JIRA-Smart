# JIRA Smart Dashboard

**AI-powered dashboard for developers and testers** — see all your assigned JIRA stories & defects in one place, click any issue to get instant, intelligent analysis of what you need to do.

---

## Architecture

```
┌─────────────────────┐      ┌──────────────────────┐      ┌─────────────┐
│   Angular Frontend  │─────▶│  Spring Boot Backend  │─────▶│  JIRA Cloud  │
│   (Port 4200)       │      │  (Port 8080)          │      │  REST API    │
└─────────────────────┘      └──────────┬───────────┘      └─────────────┘
                                        │
                                        ▼
                              ┌──────────────────┐
                              │  OpenAI GPT-4    │
                              │  (AI Analysis)   │
                              └──────────────────┘
```

## Features

- **Dashboard View** — See all your assigned stories, bugs, and tasks in one clean view
- **Stat Cards** — Quick count of stories, bugs, tasks at a glance
- **Filter & Search** — Filter by status, type, or free-text search across loaded issues
- **AI-Powered Analysis** — Click any issue and get:
  - 📋 **Overview** — What this issue is about
  - ✅ **What To Do** — Step-by-step instructions
  - 💻 **Technical Guidance** — Code areas and patterns to follow
  - 🧪 **Testing Guidance** — Test scenarios and edge cases
  - ✔️ **Acceptance Criteria Notes** — How to satisfy the AC
  - 🔗 **Dependencies** — Blockers and related work
  - ⚠️ **Risks** — Gotchas and things to watch for
  - ⏱️ **Estimated Effort** — Rough effort estimate
  - 📝 **Action Items Checklist** — Numbered todo list
- **Linked Context** — Auto-fetches and displays epic details, linked issues, comments, attachments
- **Responsive UI** — Works on desktop and tablet

---

## Tech Stack

| Layer    | Technology                     |
|----------|--------------------------------|
| Frontend | Angular 18, TypeScript, SCSS   |
| Backend  | Java 17, Spring Boot 3.3       |
| AI       | OpenAI GPT-4 API               |
| JIRA     | Atlassian JIRA Cloud REST API  |

---

## Prerequisites

- **Java 17+** (for backend)
- **Maven 3.8+** (for backend build)
- **Node.js 18+** and **npm** (for Angular frontend)
- **Angular CLI** (`npm install -g @angular/cli`)
- **JIRA Cloud** account with API token
- **OpenAI** API key

---

## Setup

### 1. Clone & Configure

```bash
# Clone the repo
cd JIRa

# Configure backend
# Edit: backend/src/main/resources/application.yml
# Set your values:
#   jira.base-url: https://your-domain.atlassian.net
#   jira.email: your-email@company.com
#   jira.api-token: your-jira-api-token
#   openai.api-key: your-openai-api-key
```

Or use environment variables:
```bash
set JIRA_BASE_URL=https://your-domain.atlassian.net
set JIRA_EMAIL=your-email@company.com
set JIRA_API_TOKEN=your-jira-api-token
set OPENAI_API_KEY=your-openai-api-key
```

### 2. Get Your JIRA API Token

1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Click **Create API token**
3. Copy the token into `application.yml` or env var

### 3. Get Your OpenAI API Key

1. Go to https://platform.openai.com/api-keys
2. Create a new key
3. Copy into `application.yml` or env var

### 4. Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**

### 5. Run Frontend

```bash
cd frontend
npm install
ng serve --open
```

Frontend starts on **http://localhost:4200**

---

## API Endpoints

| Method | Endpoint                           | Description                              |
|--------|------------------------------------|------------------------------------------|
| GET    | `/api/dashboard/issues`            | Get assigned issues (query: assignee, status, type) |
| GET    | `/api/issues/{issueKey}`           | Get full issue details                   |
| GET    | `/api/issues/{issueKey}/analyze`   | AI-powered issue analysis                |

---

## How It Works

1. **Developer/Tester** opens the dashboard and enters their JIRA email
2. **Dashboard** calls Spring Boot backend → JIRA REST API to fetch assigned issues
3. Issues appear as cards with type, status, priority badges
4. **Click "Analyze"** on any issue
5. Backend fetches:
   - Full issue details (description, comments, attachments)
   - Parent epic (if linked)
   - All linked issues with their details
6. All context is sent to **OpenAI GPT-4** with a structured prompt
7. AI returns actionable analysis in JSON format
8. Frontend renders the analysis in organized, readable sections

---

## Project Structure

```
JIRa/
├── backend/                          # Spring Boot Backend
│   ├── pom.xml
│   └── src/main/java/com/dashboard/jira/
│       ├── JiraSmartDashboardApplication.java
│       ├── config/
│       │   ├── JiraConfig.java       # JIRA WebClient config
│       │   ├── OpenAiConfig.java     # OpenAI WebClient config
│       │   └── CorsConfig.java       # CORS for Angular
│       ├── controller/
│       │   ├── DashboardController.java
│       │   └── IssueController.java
│       ├── dto/
│       │   ├── JiraIssueDto.java
│       │   ├── DashboardResponseDto.java
│       │   └── AnalysisResponseDto.java
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       └── service/
│           ├── JiraService.java      # JIRA REST API integration
│           └── AnalysisService.java  # AI analysis engine
│
├── frontend/                         # Angular Frontend
│   ├── angular.json
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── index.html
│       ├── main.ts
│       ├── styles.scss               # Global styles
│       └── app/
│           ├── app.component.ts      # Root + sidebar
│           ├── app.config.ts
│           ├── app.routes.ts
│           ├── models/
│           │   └── jira.models.ts    # TypeScript interfaces
│           ├── services/
│           │   └── jira-api.service.ts
│           └── components/
│               ├── dashboard/        # Main issue list view
│               └── issue-analysis/   # AI analysis view
│
└── README.md
```

---

## Customization

- **Change AI Model**: Update `openai.model` in `application.yml` (e.g., `gpt-4o`, `gpt-3.5-turbo`)
- **Adjust Max Results**: Change `maxResults` in `JiraService.java`  
- **Custom JIRA Fields**: Add custom field IDs in the `fields` parameter in `JiraService.java`
- **Epic Link Field**: Default is `customfield_10014` — may differ in your JIRA instance
