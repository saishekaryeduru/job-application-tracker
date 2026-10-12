import { useCallback, useEffect, useState } from 'react';
import { api, STATUSES } from '../api/client';
import type { ApplicationPayload, ApplicationStatus, JobApplication } from '../api/client';
import ApplicationForm from '../components/ApplicationForm';

type Filter = ApplicationStatus | 'ALL';

export default function DashboardPage() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [editing, setEditing] = useState<JobApplication | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setApplications(await api.listApplications(filter === 'ALL' ? undefined : filter));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load applications');
    }
  }, [filter]);

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
        </div>
        <button
          onClick={() => {
            setEditing(null);
            setShowForm((v) => !v);
          }}
        >
          {showForm ? 'Close form' : '+ Add application'}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

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
                  Nothing here yet — add your first application.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
