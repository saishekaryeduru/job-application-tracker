// Small typed client for the Job Tracker API.
// The JWT lives in localStorage; every call attaches it when present.

export type ApplicationStatus =
  | 'SAVED'
  | 'APPLIED'
  | 'SCREENING'
  | 'INTERVIEWING'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN';

export const STATUSES: ApplicationStatus[] = [
  'SAVED',
  'APPLIED',
  'SCREENING',
  'INTERVIEWING',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
];

export interface JobApplication {
  id: number;
  company: string;
  title: string;
  location: string | null;
  jobUrl: string | null;
  status: ApplicationStatus;
  appliedDate: string;
  salaryMin: number | null;
  salaryMax: number | null;
  notes: string | null;
}

export interface ApplicationPayload {
  company: string;
  title: string;
  location?: string;
  jobUrl?: string;
  status: ApplicationStatus;
  appliedDate: string;
  salaryMin?: number;
  salaryMax?: number;
  notes?: string;
}

export interface ApplicationPage {
  content: JobApplication[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListApplicationsParams {
  status?: ApplicationStatus;
  query?: string;
  page?: number;
  size?: number;
}

export interface StaleApplication extends JobApplication {
  updatedAt: string;
  daysStale: number;
}

export type InterviewOutcome = 'SCHEDULED' | 'PASSED' | 'FAILED' | 'CANCELLED' | 'NO_SHOW';

export const INTERVIEW_OUTCOMES: InterviewOutcome[] = [
  'SCHEDULED',
  'PASSED',
  'FAILED',
  'CANCELLED',
  'NO_SHOW',
];

export interface Interview {
  id: number;
  applicationId: number;
  round: string;
  scheduledAt: string;
  durationMinutes: number;
  interviewer: string | null;
  outcome: InterviewOutcome;
  notes: string | null;
  /** Markdown source, rendered client-side by the Markdown component. */
  prepNotes: string | null;
}

export interface InterviewPayload {
  round: string;
  scheduledAt: string;
  durationMinutes?: number;
  interviewer?: string;
  outcome?: InterviewOutcome;
  notes?: string;
  prepNotes?: string;
}

/** "45 min" under an hour, "1 h" / "1 h 30 min" at and above it. */
export function formatDuration(durationMinutes: number): string {
  if (durationMinutes < 60) return `${durationMinutes} min`;
  const hours = Math.floor(durationMinutes / 60);
  const minutes = durationMinutes % 60;
  return minutes === 0 ? `${hours} h` : `${hours} h ${minutes} min`;
}

/** A SCHEDULED round coming up soon, with its parent application named. */
export interface UpcomingInterview {
  id: number;
  applicationId: number;
  company: string;
  title: string;
  round: string;
  scheduledAt: string;
  durationMinutes: number;
  interviewer: string | null;
}

export interface AnalyticsSummary {
  byStatus: Record<ApplicationStatus, number>;
  responseRate: { applied: number; responded: number; rate: number };
  weekly: { weekStart: string; count: number }[];
}

export interface DigestApplication {
  id: number;
  company: string;
  title: string;
  status: ApplicationStatus;
  appliedDate: string;
}

export interface DigestInterview {
  id: number;
  applicationId: number;
  company: string;
  title: string;
  round: string;
  scheduledAt: string;
  outcome: InterviewOutcome;
}

/** A Monday-to-Sunday week as one view; the current week unless a past week is requested. */
export interface WeeklyDigest {
  weekStart: string;
  weekEnd: string;
  submitted: DigestApplication[];
  roundsHeld: DigestInterview[];
  outcomes: Record<InterviewOutcome, number>;
  followUpsDue: StaleApplication[];
  applicationsSubmitted: number;
  roundsHeldCount: number;
  followUpsDueCount: number;
}

const TOKEN_KEY = 'jobtracker.token';

export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  save: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(path, { ...init, headers: { ...headers, ...init.headers } });
  if (response.status === 401) {
    session.clear();
    throw new Error('Session expired — please log in again.');
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      /* keep the default message */
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Authenticated file download: the JWT goes in a header, so a plain link cannot do this. */
async function fetchBlob(path: string): Promise<Blob> {
  const token = session.token();
  const response = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (response.status === 401) {
    session.clear();
    throw new Error('Session expired — please log in again.');
  }
  if (!response.ok) throw new Error(`Download failed (${response.status})`);
  return response.blob();
}

/** Saves a blob as a file via a temporary object URL. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export const api = {
  register: (email: string, password: string, displayName: string) =>
    request<{ token: string; email: string; displayName: string }>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    }),

  login: (email: string, password: string) =>
    request<{ token: string; email: string; displayName: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  listApplications: (params: ListApplicationsParams = {}) => {
    const search = new URLSearchParams();
    if (params.status) search.set('status', params.status);
    if (params.query?.trim()) search.set('q', params.query.trim());
    search.set('page', String(params.page ?? 0));
    search.set('size', String(params.size ?? 20));
    return request<ApplicationPage>(`/api/applications?${search.toString()}`);
  },

  createApplication: (payload: ApplicationPayload) =>
    request<JobApplication>('/api/applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateApplication: (id: number, payload: ApplicationPayload) =>
    request<JobApplication>(`/api/applications/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  updateStatus: (id: number, status: ApplicationStatus) =>
    request<JobApplication>(`/api/applications/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  deleteApplication: (id: number) =>
    request<void>(`/api/applications/${id}`, { method: 'DELETE' }),

  // File downloads are not JSON, so they cannot go through request();
  // they share this blob fetch instead (CSV export, calendar exports).
  exportApplications: async (): Promise<Blob> => fetchBlob('/api/applications/export'),

  // FormData sets its own multipart Content-Type; request() would force JSON.
  importApplications: async (file: File): Promise<{ imported: number }> => {
    const token = session.token();
    const form = new FormData();
    form.append('file', file);
    const response = await fetch('/api/applications/import', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    });
    if (response.status === 401) {
      session.clear();
      throw new Error('Session expired — please log in again.');
    }
    if (!response.ok) {
      let message = `Import failed (${response.status})`;
      try {
        const body = await response.json();
        if (body?.message) message = body.message;
      } catch {
        /* keep the default message */
      }
      throw new Error(message);
    }
    return (await response.json()) as { imported: number };
  },

  staleApplications: (days = 10) =>
    request<StaleApplication[]>(`/api/applications/stale?days=${days}`),

  listInterviews: (applicationId: number) =>
    request<Interview[]>(`/api/applications/${applicationId}/interviews`),

  createInterview: (applicationId: number, payload: InterviewPayload) =>
    request<Interview>(`/api/applications/${applicationId}/interviews`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateInterview: (applicationId: number, interviewId: number, payload: InterviewPayload) =>
    request<Interview>(`/api/applications/${applicationId}/interviews/${interviewId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  upcomingInterviews: (withinHours = 168) =>
    request<UpcomingInterview[]>(`/api/interviews/upcoming?withinHours=${withinHours}`),

  downloadInterviewCalendar: (applicationId: number, interviewId: number) =>
    fetchBlob(`/api/applications/${applicationId}/interviews/${interviewId}/calendar.ics`),

  downloadInterviewsCalendar: () => fetchBlob('/api/interviews/calendar.ics'),

  analytics: () => request<AnalyticsSummary>('/api/analytics/summary'),

  // Any ISO date inside the wanted week works; the API snaps it to that
  // week's Monday. Omitting it returns the current week.
  weeklyDigest: (week?: string) =>
    request<WeeklyDigest>(
      week ? `/api/analytics/weekly-digest?week=${encodeURIComponent(week)}` : '/api/analytics/weekly-digest',
    ),
};
