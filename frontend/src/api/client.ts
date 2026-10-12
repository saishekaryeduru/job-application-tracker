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

export interface AnalyticsSummary {
  byStatus: Record<ApplicationStatus, number>;
  responseRate: { applied: number; responded: number; rate: number };
  weekly: { weekStart: string; count: number }[];
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

  listApplications: (status?: ApplicationStatus) =>
    request<JobApplication[]>(`/api/applications${status ? `?status=${status}` : ''}`),

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

  analytics: () => request<AnalyticsSummary>('/api/analytics/summary'),
};
