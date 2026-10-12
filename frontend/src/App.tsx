import { useState } from 'react';
import { session } from './api/client';
import AnalyticsPage from './pages/AnalyticsPage';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';

type View = 'applications' | 'analytics';

export default function App() {
  const [loggedIn, setLoggedIn] = useState<boolean>(() => Boolean(session.token()));
  const [view, setView] = useState<View>('applications');

  if (!loggedIn) {
    return <LoginPage onLoggedIn={() => setLoggedIn(true)} />;
  }

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Job Tracker</span>
        <nav className="row">
          <button
            className={view === 'applications' ? '' : 'secondary'}
            onClick={() => setView('applications')}
          >
            Applications
          </button>
          <button
            className={view === 'analytics' ? '' : 'secondary'}
            onClick={() => setView('analytics')}
          >
            Analytics
          </button>
          <button
            className="secondary"
            onClick={() => {
              session.clear();
              setLoggedIn(false);
            }}
          >
            Log out
          </button>
        </nav>
      </header>
      <main className="content">
        {view === 'applications' ? <DashboardPage /> : <AnalyticsPage />}
      </main>
    </div>
  );
}
