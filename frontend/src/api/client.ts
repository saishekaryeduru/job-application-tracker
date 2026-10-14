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

  // The export is a file download, not JSON, so it cannot go through request().
  exportApplications: async (): Promise<Blob> => {
    const token = session.token();
    const response = await fetch('/api/applications/export', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (response.status === 401) {
      session.clear();
      throw new Error('Session expired — please log in again.');
    }
    if (!response.ok) throw new Error(`Export failed (${response.status})`);
    return response.blob();
  },

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

  analytics: () => request<AnalyticsSummary>('/api/analytics/summary'),
};
