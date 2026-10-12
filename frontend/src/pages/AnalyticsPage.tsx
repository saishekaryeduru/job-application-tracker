import { useEffect, useState } from 'react';
import { AnalyticsSummary, api, STATUSES } from '../api/client';
import BarChart from '../components/BarChart';

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .analytics()
      .then(setSummary)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load analytics'));
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!summary) return <p className="muted">Loading analytics…</p>;

  const total = STATUSES.reduce((sum, s) => sum + (summary.byStatus[s] ?? 0), 0);
  const percent = Math.round(summary.responseRate.rate * 100);

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
