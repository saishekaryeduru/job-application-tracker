# Job Tracker — Smart Job Application Tracking & Analytics Platform

A full-stack web app for running a serious job search like a pipeline instead
of a pile of browser tabs. You record every application, move it through a
status pipeline (saved → applied → screening → interviewing → offer), schedule
interview rounds against it, and the analytics view tells you the numbers that
actually matter: how the pipeline is distributed, how many applications went
out each week, and your response rate.

I built this because spreadsheets stop scaling around application #40 — and
because "how is the search going?" deserves a data answer, not a feeling.

## Features

- **Accounts & auth** — register/login with bcrypt-hashed passwords and
  stateless JWT sessions. Every record is scoped to its owner; users cannot
  see each other's data (covered by tests).
- **Application tracking** — company, role, location, posting URL, applied
  date, salary range, and notes, with full create/edit/delete and one-click
  status changes.
- **Status pipeline & filtering** — filter the dashboard by any of the seven
  statuses.
- **Interviews** — schedule rounds (recruiter screen, technical, onsite…)
  against an application, with interviewer, time, outcome, and notes.
- **Analytics** — counts by status, applications per week for the last
  8 weeks, and response rate (share of applications that moved past
  "applied" into screening/interviewing/offer/rejected).
- **Consistent API errors** — one JSON error shape everywhere, with per-field
  details on validation failures.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 17, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security |
| Auth     | JWT (JJWT), BCrypt |
| Database | PostgreSQL (production), H2 (dev/test), Flyway migrations |
| Frontend | React 18, TypeScript, Vite |
| Charts   | Hand-rolled SVG components (no chart dependency) |
| Tests    | JUnit 5, Spring MockMvc integration tests |

## Architecture

```
frontend/  React + TypeScript SPA (Vite dev server proxies /api → :8080)
backend/   Spring Boot API
  ├── auth/        register/login, token issuing
  ├── security/    JWT filter, security config, current-user resolution
  ├── application/ application CRUD + status transitions
  ├── interview/   interview rounds nested under an application
  ├── analytics/   pipeline counts, weekly rate, response rate
  ├── model/       JPA entities (User, JobApplication, Interview)
  ├── repository/  Spring Data repositories
  └── common/      ApiException + global exception handler (one error shape)
```

The backend is a layered monolith on purpose: controllers stay thin, services
own the rules (e.g. salary-range validation, owner scoping), and analytics is
computed from repository queries rather than cached counters — the numbers
can't drift from the data.

## Running it locally

### Backend

Production-style (PostgreSQL + Flyway):

```bash
cd backend
docker run -d --name jobtracker-db -p 5432:5432 \
  -e POSTGRES_DB=jobtracker -e POSTGRES_USER=jobtracker -e POSTGRES_PASSWORD=jobtracker \
  postgres:16
export JWT_SECRET="replace-with-a-long-random-string"
mvn spring-boot:run
```

Quick dev mode (in-memory H2, no database to install):

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The API listens on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, create an account, and start tracking.
The Vite dev server proxies API calls to the backend.

### Tests

```bash
cd backend && mvn test     # JUnit + MockMvc suite on the H2 test profile
cd frontend && npm run build   # type-check + production build
```

## API overview

All endpoints except `/api/auth/*` require `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create an account, returns a JWT |
| POST | `/api/auth/login` | Log in, returns a JWT |
| GET | `/api/applications?status=APPLIED` | List my applications (optional status filter) |
| POST | `/api/applications` | Create an application |
| GET | `/api/applications/{id}` | Get one application |
| PUT | `/api/applications/{id}` | Update an application |
| PATCH | `/api/applications/{id}/status` | Move it through the pipeline |
| DELETE | `/api/applications/{id}` | Delete an application |
| GET | `/api/applications/{id}/interviews` | List interview rounds |
| POST | `/api/applications/{id}/interviews` | Schedule an interview round |
| GET | `/api/analytics/summary` | byStatus + responseRate + weekly, in one call |
| GET | `/api/analytics/by-status` | Counts per pipeline status |
| GET | `/api/analytics/weekly?weeks=8` | Applications per week |
| GET | `/api/analytics/response-rate` | Applied vs. responded and the rate |

## Screenshots

<!-- TODO: add screenshots of the dashboard and analytics views -->
_Dashboard and analytics screenshots coming with the v1.1 polish pass._

## Roadmap (v2 ideas)

- Reminders for stale applications ("no movement in 10 days")
- CSV import/export
- Interview prep notes with Markdown rendering
- Docker Compose for the full stack
- Pagination + full-text search on the dashboard

## License

MIT — see [LICENSE](LICENSE). Copyright © 2026 Sai Shekar Yeduru.
