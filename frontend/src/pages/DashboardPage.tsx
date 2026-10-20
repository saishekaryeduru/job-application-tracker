import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { api, saveBlob, STATUSES } from '../api/client';
import type {
  ApplicationPayload,
  ApplicationStatus,
  JobApplication,
  StaleApplication,
  UpcomingInterview,
} from '../api/client';
import ApplicationForm from '../components/ApplicationForm';
import InterviewPanel from '../components/InterviewPanel';

type Filter = ApplicationStatus | 'ALL';

const PAGE_SIZE = 20;

/** Short human lead time for the upcoming list: "in 3 hours", "tomorrow", "in 4 days". */
function startsIn(scheduledAt: string): string {
  const ms = new Date(scheduledAt).getTime() - Date.now();
  const hours = Math.round(ms / 3_600_000);
  if (hours < 1) return 'within the hour';
  if (hours < 24) return `in ${hours} hour${hours === 1 ? '' : 's'}`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'tomorrow' : `in ${days} days`;
}

export default function DashboardPage() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [searchInput, setSearchInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [stale, setStale] = useState<StaleApplication[]>([]);
  const [upcoming, setUpcoming] = useState<UpcomingInterview[]>([]);
  const [editing, setEditing] = useState<JobApplication | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const importInput = useRef<HTMLInputElement>(null);

  // Debounce the search box so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(searchInput), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // A new filter or search term restarts the list from the first page.
  useEffect(() => {
    setPage(0);
  }, [filter, query]);

  const reload = useCallback(async () => {
    try {
      const [result, staleResult, upcomingResult] = await Promise.all([
        api.listApplications({
          status: filter === 'ALL' ? undefined : filter,
          query: query || undefined,
          page,
          size: PAGE_SIZE,
        }),
        // The stale list is independent of the dashboard filter: a quiet
        // application needs a nudge even when the list is filtered away.
        api.staleApplications().catch(() => [] as StaleApplication[]),
        // Same reasoning for the next week's rounds: they stay visible
        // whatever the dashboard is filtered to.
        api.upcomingInterviews().catch(() => [] as UpcomingInterview[]),
      ]);
      setApplications(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
      setStale(staleResult);
      setUpcoming(upcomingResult);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load applications');
    }
  }, [filter, query, page]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function handleSubmit(payload: ApplicationPayload) {
    if (editing) {
      await api.updateApplication(editing.id, payload);
    } else {
      await api.createApplication(payload);
    }
    setShowForm(false);
    setEditing(null);
    await reload();
  }

  async function handleStatus(app: JobApplication, status: ApplicationStatus) {
    await api.updateStatus(app.id, status);
    await reload();
  }

  async function handleDelete(app: JobApplication) {
    if (!window.confirm(`Delete the ${app.title} application at ${app.company}?`)) return;
    await api.deleteApplication(app.id);
    await reload();
  }

  async function handleExport() {
    setError(null);
    setNotice(null);
    try {
      saveBlob(await api.exportApplications(), 'job-applications.csv');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Export failed');
    }
  }

  async function handleCalendarExport() {
    setError(null);
    setNotice(null);
    try {
      saveBlob(await api.downloadInterviewsCalendar(), 'job-tracker-interviews.ics');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Calendar export failed');
    }
  }

  async function handleImport(file: File) {
    setError(null);
    setNotice(null);
    try {
      const result = await api.importApplications(file);
      setNotice(
        `Imported ${result.imported} application${result.imported === 1 ? '' : 's'} from ${file.name}.`,
      );
      setPage(0);
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import failed');
    }
  }

  const isFiltering = filter !== 'ALL' || query.trim() !== '';

  return (
    <section>
      <div className="row space-between">
        <div className="row">
          <label>
            Filter&nbsp;
            <select value={filter} onChange={(e) => setFilter(e.target.value as Filter)}>
              <option value="ALL">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label className="search-box">
            Search&nbsp;
            <input
              type="search"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Company, role, or location"
            />
          </label>
        </div>
        <div className="row">
          <button className="secondary" onClick={() => void handleExport()}>
            Export CSV
          </button>
          <button className="secondary" onClick={() => importInput.current?.click()}>
            Import CSV
          </button>
          <input
            ref={importInput}
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={(e) => {
              const file = e.target.files?.[0];
              // Reset the input so picking the same file again re-triggers the import.
              e.target.value = '';
              if (file) void handleImport(file);
            }}
          />
          <button
            onClick={() => {
              setEditing(null);
              setShowForm((v) => !v);
            }}
          >
            {showForm ? 'Close form' : '+ Add application'}
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      {stale.length > 0 && (
        <div className="card stale-card" role="status">
          <strong>
            {stale.length} application{stale.length === 1 ? '' : 's'} with no movement in
            10+ days
          </strong>
          <ul className="stale-list">
            {stale.map((app) => (
              <li key={app.id}>
                {app.company} — {app.title} ({app.status}, {app.daysStale} days since last
                update)
              </li>
            ))}
          </ul>
          <span className="muted small">
            Worth a follow-up, a status check, or closing out if the role has gone quiet.
          </span>
        </div>
      )}

      {upcoming.length > 0 && (
        <div className="card upcoming-card" role="status">
          <div className="row space-between">
            <strong>
              {upcoming.length} interview{upcoming.length === 1 ? '' : 's'} in the next
              7 days
            </strong>
            <button className="secondary" onClick={() => void handleCalendarExport()}>
              Export calendar (.ics)
            </button>
          </div>
          <ul className="stale-list">
            {upcoming.map((interview) => (
              <li key={interview.id}>
                {interview.round} — {interview.title} at {interview.company} (
                {new Date(interview.scheduledAt).toLocaleString()},{' '}
                {startsIn(interview.scheduledAt)}
                {interview.interviewer ? `, with ${interview.interviewer}` : ''})
              </li>
            ))}
          </ul>
          <span className="muted small">
            The .ics file carries a reminder one day and one hour before each round.
          </span>
        </div>
      )}

      {showForm && (
        <ApplicationForm
          initial={editing}
          onSubmit={handleSubmit}
          onCancel={() => {
            setShowForm(false);
            setEditing(null);
          }}
        />
      )}

      <div className="card table-card">
        <table>
          <thead>
            <tr>
              <th>Company</th>
              <th>Role</th>
              <th>Applied</th>
              <th>Salary range</th>
              <th>Status</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {applications.map((app) => (
              <Fragment key={app.id}>
              <tr>
                <td>
                  {app.jobUrl ? (
                    <a href={app.jobUrl} target="_blank" rel="noreferrer">
                      {app.company}
                    </a>
                  ) : (
                    app.company
                  )}
                  {app.location && <div className="muted small">{app.location}</div>}
                </td>
                <td>{app.title}</td>
                <td>{app.appliedDate}</td>
                <td>
                  {app.salaryMin || app.salaryMax
                    ? `$${app.salaryMin?.toLocaleString() ?? '—'} – $${app.salaryMax?.toLocaleString() ?? '—'}`
                    : '—'}
                </td>
                <td>
                  <select
                    value={app.status}
                    onChange={(e) => void handleStatus(app, e.target.value as ApplicationStatus)}
                    className={`status status-${app.status.toLowerCase()}`}
                  >
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="row">
                  <button
                    className="secondary"
                    onClick={() => setExpandedId(expandedId === app.id ? null : app.id)}
                  >
                    {expandedId === app.id ? 'Hide interviews' : 'Interviews'}
                  </button>
                  <button
                    className="secondary"
                    onClick={() => {
                      setEditing(app);
                      setShowForm(true);
                    }}
                  >
                    Edit
                  </button>
                  <button className="danger" onClick={() => void handleDelete(app)}>
                    Delete
                  </button>
                </td>
              </tr>
              {expandedId === app.id && (
                <tr>
                  <td colSpan={6}>
                    <InterviewPanel applicationId={app.id} />
                  </td>
                </tr>
              )}
              </Fragment>
            ))}
            {applications.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  {isFiltering
                    ? 'No applications match the current filter and search.'
                    : 'Nothing here yet — add your first application.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {totalElements > 0 && (
        <div className="row space-between">
          <span className="muted">
            {totalElements} application{totalElements === 1 ? '' : 's'}
            {totalPages > 1 ? ` — page ${page + 1} of ${totalPages}` : ''}
          </span>
          {totalPages > 1 && (
            <span className="row">
              <button
                className="secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <button
                className="secondary"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </span>
          )}
        </div>
      )}
    </section>
  );
}
