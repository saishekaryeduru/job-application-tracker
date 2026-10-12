import { FormEvent, useState } from 'react';
import {
  ApplicationPayload,
  ApplicationStatus,
  JobApplication,
  STATUSES,
} from '../api/client';

interface Props {
  initial?: JobApplication | null;
  onSubmit: (payload: ApplicationPayload) => Promise<void>;
  onCancel: () => void;
}

/** Add/edit form for one application. Reused for both flows. */
export default function ApplicationForm({ initial, onSubmit, onCancel }: Props) {
  const [company, setCompany] = useState(initial?.company ?? '');
  const [title, setTitle] = useState(initial?.title ?? '');
  const [location, setLocation] = useState(initial?.location ?? '');
  const [jobUrl, setJobUrl] = useState(initial?.jobUrl ?? '');
  const [status, setStatus] = useState<ApplicationStatus>(initial?.status ?? 'APPLIED');
  const [appliedDate, setAppliedDate] = useState(
    initial?.appliedDate ?? new Date().toISOString().slice(0, 10),
  );
  const [salaryMin, setSalaryMin] = useState(initial?.salaryMin?.toString() ?? '');
  const [salaryMax, setSalaryMax] = useState(initial?.salaryMax?.toString() ?? '');
  const [notes, setNotes] = useState(initial?.notes ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit({
        company: company.trim(),
        title: title.trim(),
        location: location.trim() || undefined,
        jobUrl: jobUrl.trim() || undefined,
        status,
        appliedDate,
        salaryMin: salaryMin ? Number(salaryMin) : undefined,
        salaryMax: salaryMax ? Number(salaryMax) : undefined,
        notes: notes.trim() || undefined,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
      setBusy(false);
    }
  }

  return (
    <form className="card form-grid" onSubmit={submit}>
      <h2>{initial ? 'Edit application' : 'Add application'}</h2>
      <label>
        Company
        <input value={company} onChange={(e) => setCompany(e.target.value)} required />
      </label>
      <label>
        Role title
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </label>
      <label>
        Location
        <input value={location} onChange={(e) => setLocation(e.target.value)} />
      </label>
      <label>
        Posting URL
        <input value={jobUrl} onChange={(e) => setJobUrl(e.target.value)} type="url" />
      </label>
      <label>
        Status
        <select value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
      <label>
        Applied date
        <input
          type="date"
          value={appliedDate}
          onChange={(e) => setAppliedDate(e.target.value)}
          required
        />
      </label>
      <label>
        Salary min (USD)
        <input
          type="number"
          min={0}
          value={salaryMin}
          onChange={(e) => setSalaryMin(e.target.value)}
        />
      </label>
      <label>
        Salary max (USD)
        <input
          type="number"
          min={0}
          value={salaryMax}
          onChange={(e) => setSalaryMax(e.target.value)}
        />
      </label>
      <label className="wide">
        Notes
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} />
      </label>

      {error && <p className="error wide">{error}</p>}

      <div className="row wide">
        <button type="submit" disabled={busy}>
          {busy ? 'Saving…' : initial ? 'Save changes' : 'Add application'}
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
