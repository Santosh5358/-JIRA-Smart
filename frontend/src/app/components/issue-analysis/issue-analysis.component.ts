import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { JiraApiService } from '../../services/jira-api.service';
import { AnalysisResponse, JiraIssue, LinkedIssue } from '../../models/jira.models';

@Component({
  selector: 'app-issue-analysis',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './issue-analysis.component.html',
  styleUrl: './issue-analysis.component.scss'
})
export class IssueAnalysisComponent implements OnInit {
  issueKey = '';
  issueDetails: JiraIssue | null = null;
  analysis: AnalysisResponse | null = null;
  isLoadingDetails = false;
  isLoadingAnalysis = false;
  errorMessage = '';
  activeTab: 'details' | 'analysis' | 'context' | 'prs' = 'details';
  isAiFallback = false;
  lightboxImage: string | null = null;
  lightboxFilename = '';

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private jiraApi: JiraApiService
  ) {}

  ngOnInit(): void {
    // Subscribe to route params so component reacts when navigating between issues
    this.route.paramMap.subscribe(params => {
      const key = params.get('issueKey') || '';
      if (key && key !== this.issueKey) {
        this.issueKey = key;
        this.resetState();
        this.loadIssueDetails();
      } else if (key && !this.issueDetails) {
        this.issueKey = key;
        this.loadIssueDetails();
      }
    });
  }

  /** Reset component state for a fresh issue load */
  private resetState(): void {
    this.issueDetails = null;
    this.analysis = null;
    this.errorMessage = '';
    this.isLoadingDetails = false;
    this.isLoadingAnalysis = false;
    this.activeTab = 'details';
    this.isAiFallback = false;
  }

  /** Load only issue details (fast, no AI) */
  loadIssueDetails(): void {
    this.isLoadingDetails = true;
    this.errorMessage = '';

    this.jiraApi.getIssueDetails(this.issueKey).subscribe({
      next: (data) => {
        this.issueDetails = data;
        this.isLoadingDetails = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Failed to load issue details.';
        this.isLoadingDetails = false;
      }
    });
  }

  /** Run AI analysis (only on explicit user action) */
  loadAnalysis(): void {
    this.isLoadingAnalysis = true;
    this.errorMessage = '';

    this.jiraApi.analyzeIssue(this.issueKey).subscribe({
      next: (data) => {
        this.analysis = data;
        this.isLoadingAnalysis = false;
        this.isAiFallback = this.detectFallback(data);
        // Also update issueDetails from analysis response if richer
        if (data.fullIssueDetails) {
          this.issueDetails = data.fullIssueDetails;
        }
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Failed to analyze issue. Please try again.';
        this.isLoadingAnalysis = false;
      }
    });
  }

  /** Switch tab — trigger AI analysis on first click of analysis tab */
  setTab(tab: 'details' | 'analysis' | 'context' | 'prs'): void {
    this.activeTab = tab;
    if (tab === 'analysis' && !this.analysis && !this.isLoadingAnalysis) {
      this.loadAnalysis();
    }
  }

  /** Clear current results and re-run AI analysis from scratch */
  reAnalyze(): void {
    this.analysis = null;
    this.errorMessage = '';
    this.isAiFallback = false;
    this.activeTab = 'analysis';
    this.loadAnalysis();
  }

  /** Detect if the response is the default fallback (AI service was unreachable) */
  private detectFallback(data: AnalysisResponse): boolean {
    if (!data.tldr) return false;
    const t = data.tldr.toLowerCase();
    return t.includes('ai service') && (t.includes('unreachable') || t.includes('unable'));
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  get isLoading(): boolean {
    return this.isLoadingDetails || this.isLoadingAnalysis;
  }

  getTypeBadgeClass(type: string): string {
    switch (type?.toLowerCase()) {
      case 'story': return 'badge-story';
      case 'bug': return 'badge-bug';
      case 'task': return 'badge-task';
      case 'epic': return 'badge-epic';
      default: return 'badge-default';
    }
  }

  getStatusBadgeClass(status: string): string {
    const s = (status || '').toLowerCase();
    if (s.includes('done') || s.includes('closed')) return 'badge-status-done';
    if (s.includes('progress')) return 'badge-status-progress';
    if (s.includes('review')) return 'badge-status-review';
    return 'badge-status-todo';
  }

  /** Group linked issues by issue type category */
  getLinkedGroups(): { category: string; icon: string; color: string; items: LinkedIssue[] }[] {
    if (!this.issueDetails?.linkedIssues?.length) return [];

    const categoryOrder = ['Epic', 'Story', 'Bug', 'Defect', 'Task', 'Document', 'Other'];
    const categoryConfig: Record<string, { icon: string; color: string }> = {
      'Epic':     { icon: 'auto_awesome', color: '#6554c0' },
      'Story':    { icon: 'auto_stories', color: '#36b37e' },
      'Bug':      { icon: 'bug_report',   color: '#ff5630' },
      'Defect':   { icon: 'bug_report',   color: '#ff5630' },
      'Task':     { icon: 'task_alt',      color: '#0065ff' },
      'Document': { icon: 'description',   color: '#ff991f' },
      'Other':    { icon: 'link',          color: '#8993a4' },
    };

    const groups = new Map<string, LinkedIssue[]>();
    for (const item of this.issueDetails.linkedIssues) {
      const t = (item.issueType || '').toLowerCase();
      let cat = 'Other';
      if (t.includes('epic')) cat = 'Epic';
      else if (t.includes('story') || t.includes('user story')) cat = 'Story';
      else if (t.includes('bug')) cat = 'Bug';
      else if (t.includes('defect')) cat = 'Defect';
      else if (t.includes('task') || t.includes('sub-task')) cat = 'Task';
      else if (t.includes('document') || t.includes('doc') || t.includes('requirement')
            || t.includes('spec') || t.includes('test plan') || t.includes('initiative')) cat = 'Document';
      if (!groups.has(cat)) groups.set(cat, []);
      groups.get(cat)!.push(item);
    }

    return categoryOrder
      .filter(cat => groups.has(cat))
      .map(cat => ({
        category: cat === 'Bug' || cat === 'Defect' ? 'Bugs / Defects' : cat + 's',
        icon: categoryConfig[cat].icon,
        color: categoryConfig[cat].color,
        items: groups.get(cat)!
      }));
  }

  /** Get Material icon for issue type */
  getTypeIcon(type: string): string {
    const t = (type || '').toLowerCase();
    if (t.includes('epic')) return 'auto_awesome';
    if (t.includes('story')) return 'auto_stories';
    if (t.includes('bug') || t.includes('defect')) return 'bug_report';
    if (t.includes('task') || t.includes('sub-task')) return 'task_alt';
    if (t.includes('doc') || t.includes('requirement') || t.includes('spec')) return 'description';
    return 'link';
  }

  /** Check if AI produced meaningful PR analysis (not just "No PRs") */
  hasPrAnalysis(): boolean {
    if (!this.analysis?.prSummary) return false;
    const s = this.analysis.prSummary.toLowerCase();
    return !s.includes('no prs') && !s.includes('no pull requests') && s.trim().length > 10;
  }

  /** Get color for priority */
  getPriorityColor(priority: string): string {
    const p = (priority || '').toLowerCase();
    if (p.includes('blocker') || p.includes('highest')) return '#ff5630';
    if (p.includes('critical') || p.includes('high')) return '#ff7452';
    if (p.includes('medium') || p.includes('normal')) return '#ffab00';
    if (p.includes('low')) return '#36b37e';
    if (p.includes('lowest') || p.includes('trivial')) return '#8993a4';
    return '#8993a4';
  }

  /** Check if an attachment is an image */
  isImage(mimeType: string): boolean {
    return (mimeType || '').startsWith('image/');
  }

  /** Get image attachments for gallery display */
  getImageAttachments(): any[] {
    return (this.issueDetails?.attachments || []).filter(a => this.isImage(a.mimeType));
  }

  /** Open image in lightbox overlay */
  openLightbox(contentUrl: string, mimeType: string, filename: string): void {
    this.lightboxImage = this.getProxyUrl(contentUrl, mimeType);
    this.lightboxFilename = filename;
    document.body.style.overflow = 'hidden';
  }

  /** Close lightbox */
  closeLightbox(): void {
    this.lightboxImage = null;
    this.lightboxFilename = '';
    document.body.style.overflow = '';
  }

  /** Check if an attachment is a text-based file */
  isTextFile(mimeType: string): boolean {
    const m = (mimeType || '').toLowerCase();
    return m.startsWith('text/') || m.includes('json') || m.includes('xml')
        || m.includes('sql') || m.includes('yaml') || m.includes('csv')
        || m.includes('javascript') || m.includes('markdown') || m.includes('log');
  }

  /** Get proxied URL for an attachment through backend */
  getProxyUrl(contentUrl: string, mimeType: string): string {
    return this.jiraApi.getAttachmentProxyUrl(contentUrl, mimeType);
  }

  /** Get Material icon for a file type */
  getFileIcon(mimeType: string, filename: string): string {
    const m = (mimeType || '').toLowerCase();
    const f = (filename || '').toLowerCase();
    if (m.startsWith('image/')) return 'image';
    if (m.includes('pdf')) return 'picture_as_pdf';
    if (m.includes('word') || f.endsWith('.doc') || f.endsWith('.docx')) return 'description';
    if (m.includes('excel') || m.includes('spreadsheet') || f.endsWith('.xls') || f.endsWith('.xlsx') || f.endsWith('.csv')) return 'table_chart';
    if (m.includes('powerpoint') || m.includes('presentation') || f.endsWith('.ppt') || f.endsWith('.pptx')) return 'slideshow';
    if (m.includes('zip') || m.includes('tar') || m.includes('compressed')) return 'folder_zip';
    if (m.includes('json') || m.includes('xml') || m.includes('sql') || m.includes('javascript')) return 'data_object';
    if (m.startsWith('text/') || f.endsWith('.log') || f.endsWith('.txt') || f.endsWith('.md')) return 'article';
    if (m.includes('video/')) return 'videocam';
    return 'insert_drive_file';
  }

  /** Get file extension color class */
  getFileColorClass(mimeType: string, filename: string): string {
    const m = (mimeType || '').toLowerCase();
    const f = (filename || '').toLowerCase();
    if (m.startsWith('image/')) return 'file-color-image';
    if (m.includes('pdf')) return 'file-color-pdf';
    if (m.includes('word') || f.endsWith('.doc') || f.endsWith('.docx')) return 'file-color-word';
    if (m.includes('excel') || m.includes('spreadsheet') || f.endsWith('.xls') || f.endsWith('.csv')) return 'file-color-excel';
    if (m.includes('json') || m.includes('xml') || m.includes('sql')) return 'file-color-code';
    if (m.startsWith('text/') || f.endsWith('.log') || f.endsWith('.txt') || f.endsWith('.md')) return 'file-color-text';
    return 'file-color-default';
  }

  /** Format file size for display */
  formatFileSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  /**
   * Format AI-generated text into readable HTML.
   * Handles: numbered lists (1. 2. 3.), bullet lists (- *), **bold**, `code`, headings, etc.
   */
  formatAiText(text: string): string {
    if (!text) return '';

    let html = text;

    // Normalize line endings
    html = html.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    // Escape HTML
    html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    // Code blocks: ```lang ... ``` or ``` ... ```
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, lang, code) => {
      const langLabel = lang ? `<span class="ai-code-lang">${lang}</span>` : '';
      return `<div class="ai-code-block">${langLabel}<pre><code>${code.trim()}</code></pre></div>`;
    });

    // Inline code: `text`
    html = html.replace(/`([^`]+)`/g, '<code class="ai-inline-code">$1</code>');

    // Bold: **text**
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic: *text* (but not inside words)
    html = html.replace(/(?<!\w)\*([^*]+)\*(?!\w)/g, '<em>$1</em>');

    // Headers: ### text, ## text (AI sometimes uses markdown headers)
    html = html.replace(/^###\s+(.+)$/gm, '<h4 class="ai-heading">$1</h4>');
    html = html.replace(/^##\s+(.+)$/gm, '<h3 class="ai-heading">$1</h3>');

    // Scenario → Impact pattern (edge cases)
    html = html.replace(/SCENARIO:\s*(.*?)\s*(?:→|->|=&gt;)\s*IMPACT:\s*(.*)/gi, (_m, scenario, impact) => {
      return `<div class="scenario-split"><span class="scenario-label">SCENARIO</span><span class="scenario-desc">${scenario.trim()}</span></div>` +
             `<div class="scenario-split impact-split"><span class="impact-label">IMPACT</span><span class="impact-desc">${impact.trim()}</span></div>`;
    });

    // Test → Expect pattern (test cases)
    html = html.replace(/TEST:\s*(.*?)\s*(?:→|->|=&gt;)\s*EXPECT:\s*(.*)/gi, (_m, test, expect) => {
      return `<div class="scenario-split"><span class="scenario-label test-label-tag">TEST</span><span class="scenario-desc">${test.trim()}</span></div>` +
             `<div class="scenario-split expect-split"><span class="impact-label expect-label-tag">EXPECT</span><span class="impact-desc">${expect.trim()}</span></div>`;
    });

    // Numbered lists: lines starting with 1. 2. 3. etc.
    html = html.replace(/^(\d+)\.\s+(.+)$/gm, (_m, num, content) => {
      return `<div class="ai-step"><span class="ai-step-num">${num}</span><span class="ai-step-text">${content}</span></div>`;
    });

    // Bullet lists: lines starting with - or •
    html = html.replace(/^\s*[-•]\s+(.+)$/gm, (_m, content) => {
      return `<div class="ai-bullet"><span class="ai-bullet-dot"></span><span class="ai-bullet-text">${content}</span></div>`;
    });

    // Convert remaining newlines to <br> (outside code blocks)
    const parts = html.split(/(<div class="ai-code-block">[\s\S]*?<\/div>)/g);
    html = parts.map(part => {
      if (part.startsWith('<div class="ai-code-block">')) return part;
      return part.replace(/\n/g, '<br>');
    }).join('');

    return html;
  }

  /**
   * Convert JIRA wiki markup to HTML for rich rendering.
   * Handles: {code}, {noformat}, *bold*, _italic_, bullet lists, headings, links, etc.
   */
  formatJiraMarkup(text: string): string {
    if (!text) return '';

    let html = text;

    // Normalize line endings
    html = html.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    // Escape HTML special chars first (except we'll add our own tags)
    html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    // {code:lang}...{code} → <pre><code>...</code></pre>
    html = html.replace(/\{code(?::([a-zA-Z]+))?\}([\s\S]*?)\{code\}/g, (_m, lang, code) => {
      const langLabel = lang ? `<span class="code-lang">${lang}</span>` : '';
      // Pretty-print JSON found within the code block (handles mixed content)
      const formatted = this.prettyPrintJsonInCodeBlock(code.trim());
      return `<div class="jira-code-block">${langLabel}<pre><code>${formatted}</code></pre></div>`;
    });

    // {noformat}...{noformat} → <pre>...</pre>
    html = html.replace(/\{noformat\}([\s\S]*?)\{noformat\}/g, (_m, content) => {
      return `<div class="jira-noformat"><pre>${content.trim()}</pre></div>`;
    });

    // Auto-detect and wrap code blocks (JSON, XML, API calls, etc.)
    html = this.wrapDetectedCodeBlocks(html);

    // {quote}...{quote}
    html = html.replace(/\{quote\}([\s\S]*?)\{quote\}/g, (_m, content) => {
      return `<blockquote class="jira-quote">${content.trim()}</blockquote>`;
    });

    // {color:xxx}...{color}
    html = html.replace(/\{color:([^}]+)\}([\s\S]*?)\{color\}/g, (_m, color, content) => {
      return `<span style="color:${color}">${content}</span>`;
    });

    // Headings: h1. through h6. (may have leading whitespace)
    html = html.replace(/^\s*h([1-6])\.\s*(.+)$/gm, (_m, level, content) => {
      return `<h${level} class="jira-heading">${content.trim()}</h${level}>`;
    });

    // Remove empty bullet lines (lone * or ** with no content, possibly with leading spaces)
    html = html.replace(/^\s*\*{1,3}\s*$/gm, '');

    // Bullet lists: lines starting with * or ** (possibly with leading spaces, BEFORE bold)
    html = html.replace(/^\s*(\*{1,3})\s+(.+)$/gm, (_m, bullets, content) => {
      const depth = bullets.length;
      return `<li class="jira-bullet jira-indent-${depth}">${content.trim()}</li>`;
    });
    // Wrap consecutive <li> in <ul>
    html = html.replace(/((?:<li class="jira-bullet[^"]*">[^]*?<\/li>\n?)+)/g, '<ul class="jira-list">$1</ul>');

    // Numbered lists: lines starting with # or ## (possibly with leading spaces)
    html = html.replace(/^\s*(#+)\s+(.+)$/gm, (_m, hashes, content) => {
      const depth = hashes.length;
      return `<li class="jira-numbered jira-indent-${depth}">${content.trim()}</li>`;
    });
    html = html.replace(/((?:<li class="jira-numbered[^"]*">[^]*?<\/li>\n?)+)/g, '<ol class="jira-list">$1</ol>');

    // Bold standalone lines as sub-headings (possibly with leading/trailing spaces)
    html = html.replace(/^\s*\*([^\s*][^*]*[^\s*])\*\s*$/gm, '<div class="jira-subheading">$1</div>');

    // Bold: *text* (inline)
    html = html.replace(/\*([^\s*](?:[^*]*[^\s*])?)\*/g, '<strong>$1</strong>');

    // Italic: _text_
    html = html.replace(/(?<![a-zA-Z0-9])_([^\s_](?:[^_]*[^\s_])?)_(?![a-zA-Z0-9])/g, '<em>$1</em>');

    // Strikethrough: -text-
    html = html.replace(/(?<![a-zA-Z0-9])-([^\s-](?:[^-]*[^\s-])?)-(?![a-zA-Z0-9])/g, '<del>$1</del>');

    // Monospace: {{text}}
    html = html.replace(/\{\{([^}]+)\}\}/g, '<code class="jira-inline-code">$1</code>');

    // Links: [text|url] or [url]
    html = html.replace(/\[([^|\]]+)\|([^\]]+)\]/g, '<a href="$2" target="_blank" rel="noopener" class="jira-link">$1</a>');
    html = html.replace(/\[([^\]]+)\]/g, (_m, url) => {
      if (url.startsWith('http')) {
        return `<a href="${url}" target="_blank" rel="noopener" class="jira-link">${url}</a>`;
      }
      return `[${url}]`;
    });

    // Horizontal rule: ----
    html = html.replace(/^-{4,}$/gm, '<hr class="jira-hr">');

    // Clean up multiple consecutive blank lines
    html = html.replace(/\n{3,}/g, '\n\n');

    // Line breaks: preserve newlines as <br> (but not inside <pre>)
    // Split on pre blocks, only convert newlines outside them
    const parts = html.split(/(<div class="jira-(?:code-block|noformat)">[\s\S]*?<\/div>)/g);
    html = parts.map(part => {
      if (part.startsWith('<div class="jira-')) return part;
      return part.replace(/\n/g, '<br>');
    }).join('');

    return html;
  }

  /**
   * Detect and wrap code-like content that isn't in {code} tags.
   * Handles: multi-line JSON, single-line JSON, XML, SQL, API endpoints.
   */
  private wrapDetectedCodeBlocks(text: string): string {
    if (!text) return text;

    const lines = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
    const result: string[] = [];
    let codeBuffer: string[] = [];
    let braceDepth = 0;
    let bracketDepth = 0;
    let inCodeBlock = false;
    let inExistingBlock = false;

    const flushCodeBuffer = () => {
      if (codeBuffer.length === 0) return;
      const blockText = codeBuffer.join('\n');
      const jsonKeyCount = (blockText.match(/"[^"]*"\s*:/g) || []).length;
      if (jsonKeyCount >= 2) {
        // Pretty-print single-line JSON for readability
        const formatted = this.tryPrettyPrintJson(blockText);
        const langLabel = '<span class="code-lang">json</span>';
        result.push(`<div class="jira-code-block">${langLabel}<pre><code>${formatted}</code></pre></div>`);
      } else {
        result.push(...codeBuffer);
      }
      codeBuffer = [];
      braceDepth = 0;
      bracketDepth = 0;
    };

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Skip lines inside already-rendered code/noformat blocks
      if (line.includes('<div class="jira-code-block">') || line.includes('<div class="jira-noformat">')) {
        inExistingBlock = true;
      }
      if (inExistingBlock) {
        result.push(line);
        if (line.includes('</div>')) inExistingBlock = false;
        continue;
      }

      const trimmed = line.trim();

      // === API endpoint lines: GET/POST/PUT/DELETE/PATCH URL ===
      if (!inCodeBlock && /^\s*(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)\s+\S+/i.test(trimmed)) {
        // Collect the endpoint line + any following JSON body
        const endpointLines: string[] = [line];
        let j = i + 1;
        // Check if next non-empty line starts a JSON body
        while (j < lines.length && lines[j].trim() === '') { endpointLines.push(lines[j]); j++; }
        if (j < lines.length && /^\s*[\{\[]/.test(lines[j].trim())) {
          // There's a JSON body following — let the JSON detector handle it
          // Just wrap the endpoint as code
          result.push(`<div class="jira-code-block"><span class="code-lang">http</span><pre><code>${line}</code></pre></div>`);
          continue;
        } else {
          result.push(`<div class="jira-code-block"><span class="code-lang">http</span><pre><code>${line}</code></pre></div>`);
          continue;
        }
      }

      // === Detect start of JSON block ===
      if (!inCodeBlock) {
        const startsWithBrace = /^\s*[\{\[]/.test(trimmed);
        const hasJsonKey = /"[^"]*"\s*:/.test(trimmed);

        if (startsWithBrace && (hasJsonKey || /^\s*[\{\[]\s*$/.test(trimmed))) {
          inCodeBlock = true;
          braceDepth = 0;
          bracketDepth = 0;
          codeBuffer = [];
        }
        // Also detect a line that IS a single-line JSON: { "key": ..., "key2": ... }
        else if (hasJsonKey && (trimmed.includes('{') || trimmed.includes('['))) {
          const braces = (trimmed.match(/\{/g) || []).length - (trimmed.match(/\}/g) || []).length;
          const brackets = (trimmed.match(/\[/g) || []).length - (trimmed.match(/\]/g) || []).length;
          if (braces === 0 && brackets === 0 && (trimmed.match(/"[^"]*"\s*:/g) || []).length >= 2) {
            // Self-contained single-line JSON
            const formatted = this.tryPrettyPrintJson(trimmed);
            const langLabel = '<span class="code-lang">json</span>';
            result.push(`<div class="jira-code-block">${langLabel}<pre><code>${formatted}</code></pre></div>`);
            continue;
          } else if (braces > 0 || brackets > 0) {
            // Unbalanced — start of a multi-line JSON that begins mid-line
            inCodeBlock = true;
            braceDepth = 0;
            bracketDepth = 0;
            codeBuffer = [];
          }
        }
        // XML detection
        else if (/^\s*&lt;\?xml/.test(trimmed) || (/^\s*&lt;[a-zA-Z][\w-]*/.test(trimmed) && /&gt;\s*$/.test(trimmed))) {
          inCodeBlock = true;
          braceDepth = 0;
          bracketDepth = 0;
          codeBuffer = [];
        }
      }

      if (inCodeBlock) {
        codeBuffer.push(line);

        // Track brace/bracket depth
        // Only count outside of strings (simplified: just count all)
        for (const ch of trimmed) {
          if (ch === '{') braceDepth++;
          else if (ch === '}') braceDepth--;
          else if (ch === '[') bracketDepth++;
          else if (ch === ']') bracketDepth--;
        }

        const balanced = braceDepth <= 0 && bracketDepth <= 0;
        const isLastLine = i === lines.length - 1;

        if (balanced || isLastLine) {
          inCodeBlock = false;
          flushCodeBuffer();
          continue;
        }
      } else {
        result.push(line);
      }
    }

    // Flush anything remaining
    if (codeBuffer.length > 0) {
      inCodeBlock = false;
      flushCodeBuffer();
    }

    return result.join('\n');
  }

  /**
   * Pretty-print JSON portions found within {code} block content.
   * Handles mixed content (e.g., POST url followed by JSON body).
   */
  private prettyPrintJsonInCodeBlock(text: string): string {
    // First, try the whole content as JSON
    const wholeAttempt = this.tryPrettyPrintJson(text);
    if (wholeAttempt !== text) return wholeAttempt;

    // Otherwise, scan line by line to find and pretty-print JSON sections in-place
    const lines = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
    const result: string[] = [];
    let i = 0;

    while (i < lines.length) {
      const trimmed = lines[i].trim();

      // Check if this line starts a JSON object/array
      const startsWithBrace = /^\s*[\{\[]/.test(trimmed);
      const hasJsonKey = /"[^"]*"\s*:/.test(trimmed);

      if (startsWithBrace && (hasJsonKey || /^\s*[\{\[]\s*$/.test(trimmed))) {
        // Collect lines until braces are balanced
        let braceDepth = 0, bracketDepth = 0;
        const jsonLines: string[] = [];
        let j = i;

        while (j < lines.length) {
          jsonLines.push(lines[j]);
          for (const ch of lines[j]) {
            if (ch === '{') braceDepth++;
            else if (ch === '}') braceDepth--;
            else if (ch === '[') bracketDepth++;
            else if (ch === ']') bracketDepth--;
          }
          if (braceDepth <= 0 && bracketDepth <= 0) break;
          j++;
        }

        const jsonText = jsonLines.join('\n');
        result.push(this.tryPrettyPrintJson(jsonText));
        i = j + 1;
      } else {
        result.push(lines[i]);
        i++;
      }
    }

    return result.join('\n');
  }

  /**
   * Try to pretty-print a JSON string. If it fails, return the original.
   * Works with HTML-escaped content (&amp; &lt; &gt;).
   */
  private tryPrettyPrintJson(text: string): string {
    // Unescape HTML entities for JSON parsing
    let raw = text
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>');

    try {
      const parsed = JSON.parse(raw);
      const pretty = JSON.stringify(parsed, null, 2);
      // Re-escape for HTML
      return pretty
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    } catch {
      // Not valid JSON — return original as-is
      return text;
    }
  }
}
