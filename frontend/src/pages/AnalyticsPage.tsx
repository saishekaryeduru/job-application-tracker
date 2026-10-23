import { useEffect, useState } from 'react';
import { api, INTERVIEW_OUTCOMES, STATUSES } from '../api/client';
import type { AnalyticsSummary, WeeklyDigest } from '../api/client';
import BarChart from '../components/BarChart';

/** Shifts a yyyy-MM-dd date by whole days, in UTC so the result never slips a day across timezones. */
function shiftIsoDate(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** The Monday (yyyy-MM-dd) of the week containing today, in local time. */
function currentWeekStart(): string {
  const today = new Date();
  const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const day = monday.getDay(); // 0 = Sunday
  monday.setDate(monday.getDate() - (day === 0 ? 6 : day - 1));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${monday.getFullYear()}-${pad(monday.getMonth() + 1)}-${pad(monday.getDate())}`;
}

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [digest, setDigest] = useState<WeeklyDigest | null>(null);
  // The Monday of the week the digest is showing; undefined means "ask the
  // API for the current week" rather than pinning a date on first load.
  const [digestWeek, setDigestWeek] = useState<string | undefined>(undefined);
  const [digestError, setDigestError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .analytics()
      .then(setSummary)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load analytics'));
  }, []);

  useEffect(() => {
    let cancelled = false;
    api
      .weeklyDigest(digestWeek)
      .then((result) => {
        if (!cancelled) {
          setDigest(result);
          setDigestError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setDigestError(err instanceof Error ? err.message : 'Failed to load the weekly digest');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [digestWeek]);

  if (error) return <p className="error">{error}</p>;
  if (!summary) return <p className="muted">Loading analytics…</p>;

  const total = STATUSES.reduce((sum, s) => sum + (summary.byStatus[s] ?? 0), 0);
  const percent = Math.round(summary.responseRate.rate * 100);
  // Outcomes that were actually recorded in the displayed week, in pipeline order.
  const recordedOutcomes = digest
    ? INTERVIEW_OUTCOMES.filter((outcome) => (digest.outcomes[outcome] ?? 0) > 0)
    : [];
  const viewingCurrentWeek = digest != null && digest.weekStart === currentWeekStart();

  return (
    <section>
      <div className="stat-row">
        <div className="card stat">
          <span className="stat-value">{total}</span>
          <span className="muted">Total tracked</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{summary.responseRate.applied}</span>
          <span className="muted">Applied</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{summary.responseRate.responded}</span>
          <span className="muted">Got a response</span>
        </div>
        <div className="card stat">
          <span className="stat-value">{percent}%</span>
          <span className="muted">Response rate</span>
        </div>
      </div>

      {digestError && <p className="error">{digestError}</p>}
      {digest && (
        <div className="card">
          <div className="row space-between">
            <h2>
              {viewingCurrentWeek ? 'This week' : 'Week'} ({digest.weekStart} to {digest.weekEnd})
            </h2>
            <div className="row">
              <button
                type="button"
                className="secondary"
                onClick={() => setDigestWeek(shiftIsoDate(digest.weekStart, -7))}
              >
                Previous week
              </button>
              {!viewingCurrentWeek && (
                <>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setDigestWeek(shiftIsoDate(digest.weekStart, 7))}
                  >
                    Next week
                  </button>
                  <button type="button" onClick={() => setDigestWeek(undefined)}>
                    This week
                  </button>
                </>
              )}
            </div>
          </div>
          <p className="muted">
            {digest.applicationsSubmitted} application
            {digest.applicationsSubmitted === 1 ? '' : 's'} submitted,{' '}
            {digest.roundsHeldCount} round{digest.roundsHeldCount === 1 ? '' : 's'} held,{' '}
            {digest.followUpsDueCount} follow-up{digest.followUpsDueCount === 1 ? '' : 's'} due.
          </p>

          <h3>Submitted</h3>
          {digest.submitted.length > 0 ? (
            <ul className="stale-list">
              {digest.submitted.map((app) => (
                <li key={app.id}>
                  {app.company} — {app.title} ({app.status}, {app.appliedDate})
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">
              {viewingCurrentWeek
                ? 'No applications submitted this week yet.'
                : 'No applications submitted that week.'}
            </p>
          )}

          <h3>Rounds held</h3>
          {digest.roundsHeld.length > 0 ? (
            <ul className="stale-list">
              {digest.roundsHeld.map((round) => (
                <li key={round.id}>
                  {round.round} — {round.title} at {round.company} (
                  {new Date(round.scheduledAt).toLocaleString()}, {round.outcome})
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">
              {viewingCurrentWeek ? 'No rounds held this week yet.' : 'No rounds held that week.'}
            </p>
          )}
          {recordedOutcomes.length > 0 && (
            <p className="muted small">
              Outcomes recorded:{' '}
              {recordedOutcomes
                .map((outcome) => `${outcome} ${digest.outcomes[outcome]}`)
                .join(', ')}
            </p>
          )}

          <h3>Follow-ups due{viewingCurrentWeek ? '' : ' (current)'}</h3>
          {!viewingCurrentWeek && (
            <p className="muted small">
              Follow-ups are always a snapshot of right now — staleness is measured from each
              record&apos;s last update, which cannot be reconstructed for a past week.
            </p>
          )}
          {digest.followUpsDue.length > 0 ? (
            <ul className="stale-list">
              {digest.followUpsDue.map((app) => (
                <li key={app.id}>
                  {app.company} — {app.title} ({app.status}, {app.daysStale} days since last
                  update)
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">Nothing has gone quiet — no follow-ups due.</p>
          )}
        </div>
      )}

      <div className="card">
        <BarChart
          title="Applications by status"
          bars={STATUSES.map((s) => ({ label: s, value: summary.byStatus[s] ?? 0 }))}
        />
      </div>

      <div className="card">
        <BarChart
          title="Applications per week (last 8 weeks)"
          color="#059669"
          bars={summary.weekly.map((w) => ({
            label: w.weekStart.slice(5), // MM-DD
            value: w.count,
          }))}
        />
      </div>
    </section>
  );
}
