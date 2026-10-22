import { useEffect, useState } from 'react';
import { api, INTERVIEW_OUTCOMES, STATUSES } from '../api/client';
import type { AnalyticsSummary, WeeklyDigest } from '../api/client';
import BarChart from '../components/BarChart';

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [digest, setDigest] = useState<WeeklyDigest | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.analytics(), api.weeklyDigest().catch(() => null)])
      .then(([summaryResult, digestResult]) => {
        setSummary(summaryResult);
        setDigest(digestResult);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load analytics'));
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!summary) return <p className="muted">Loading analytics…</p>;

  const total = STATUSES.reduce((sum, s) => sum + (summary.byStatus[s] ?? 0), 0);
  const percent = Math.round(summary.responseRate.rate * 100);
  // Outcomes that were actually recorded this week, in pipeline order.
  const recordedOutcomes = digest
    ? INTERVIEW_OUTCOMES.filter((outcome) => (digest.outcomes[outcome] ?? 0) > 0)
    : [];

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

      {digest && (
        <div className="card">
          <h2>
            This week ({digest.weekStart} to {digest.weekEnd})
          </h2>
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
            <p className="muted">No applications submitted this week yet.</p>
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
            <p className="muted">No rounds held this week yet.</p>
          )}
          {recordedOutcomes.length > 0 && (
            <p className="muted small">
              Outcomes recorded:{' '}
              {recordedOutcomes
                .map((outcome) => `${outcome} ${digest.outcomes[outcome]}`)
                .join(', ')}
            </p>
          )}

          <h3>Follow-ups due</h3>
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
