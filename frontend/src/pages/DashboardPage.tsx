import { useCallback, useEffect, useRef, useState } from 'react';
import { api, STATUSES } from '../api/client';
import type { ApplicationPayload, ApplicationStatus, JobApplication } from '../api/client';
import ApplicationForm from '../components/ApplicationForm';

type Filter = ApplicationStatus | 'ALL';

const PAGE_SIZE = 20;

export default function DashboardPage() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [searchInput, setSearchInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [editing, setEditing] = useState<JobApplication | null>(null);
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
      const result = await api.listApplications({
        status: filter === 'ALL' ? undefined : filter,
        query: query || undefined,
        page,
        size: PAGE_SIZE,
      });
      setApplications(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
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
      const blob = await api.exportApplications();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'job-applications.csv';
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Export failed');
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
              <tr key={app.id}>
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
