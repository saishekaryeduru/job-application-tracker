# Job Tracker

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
- **Pagination & search** — the dashboard list is paged (20 per page, newest
  first) and searchable: `q` matches company, role title, or location as a
  case-insensitive substring, and composes with the status filter.
- **CSV import/export** — export every application to a CSV file (RFC 4180
  quoting) and import the same format back. Import is all-or-nothing: every
  row is validated first, errors are reported with their spreadsheet row
  numbers, and a bad file saves nothing.
- **Stale-application reminders** — the dashboard flags applications in
  APPLIED, SCREENING, or INTERVIEWING that have had no movement in 10+ days,
  most stale first, so quiet roles get a follow-up instead of being forgotten.
- **Interviews** — schedule rounds (recruiter screen, technical, onsite…)
  against an application, with interviewer, time, outcome, and notes.
  Rounds can be edited after the fact, not just created.
- **Upcoming interviews & calendar export** — the dashboard surfaces
  SCHEDULED rounds starting in the next 7 days across all applications,
  soonest first. Any round — or every future round at once — exports as
  an RFC 5545 `.ics` file for Google/Apple/Outlook calendars, with a
  display reminder one day and one hour before each round.
- **Interview prep notes** — a Markdown prep sheet per round: focus areas,
  stories to rehearse, questions to ask the panel. Edited as source with a
  live preview; rendering supports headings, lists, emphasis, code, and
  links from a safe subset, so stored notes can never inject markup.
- **Analytics** — counts by status, applications per week for the last
  8 weeks, and response rate (share of applications that moved past
  "applied" into screening/interviewing/offer/rejected).
- **Consistent API errors** — one JSON error shape everywhere, with per-field
  details on validation failures.
- **One-command run** — Docker Compose brings up PostgreSQL, the API, and
  the frontend together; no local Java, Node, or Postgres install needed.

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
  ├── interview/   interview rounds, upcoming list, .ics calendar export
  ├── analytics/   pipeline counts, weekly rate, response rate
  ├── model/       JPA entities (User, JobApplication, Interview)
  ├── repository/  Spring Data repositories
  └── common/      ApiException + global exception handler (one error shape)
```

The backend is a layered monolith on purpose: controllers stay thin, services
own the rules (e.g. salary-range validation, owner scoping), repositories are
the only code that talks to JPA.

## Design decisions

- **Stateless JWT sessions.** No session store and no sticky sessions; any
  instance can serve any request. The tradeoff is revocation — a token stays
  valid until it expires — so the expiry is kept to 24 hours and passwords
  are bcrypt-hashed.
- **Analytics computed at request time.** Counts, weekly volume, and the
  response rate come from repository queries against the applications table
  on every call, so the numbers can't drift from the data. That costs a few
  extra count queries per analytics request; at single-user scale that is
  the right side of the tradeoff. If this ever served real traffic, weekly
  counts are the first thing I'd cache.
- **Stable pagination.** The list sorts by applied date descending with the
  record id as a tie-breaker. Without the second key, applications that share
  a date could appear on two pages (or none) as the data changes underneath
  the pager.
- **Hand-rolled SVG charts.** Two small components (~60 lines) instead of a
  chart dependency. No tooltips or animations, but also nothing extra to
  ship, pin, or audit for two bar charts.
- **Staleness is measured from `updatedAt`, not the applied date.** Every
  edit and status change refreshes `updatedAt`, so an application the user
  followed up on yesterday does not keep flagging just because it was
  submitted weeks ago. Only active stages (APPLIED, SCREENING,
  INTERVIEWING) can go stale: SAVED has not been submitted, and OFFER /
  REJECTED / WITHDRAWN need no follow-up nudge.
- **No CSV library.** The export/import format is nine flat columns, so the
  RFC 4180 quoting and parsing are ~80 lines of standard-library code
  instead of a dependency. The interesting decision is the import contract,
  not the parsing: validate the whole file up front, report errors by
  spreadsheet row number, and save nothing if any row is bad — a partial
  import would leave the tracker silently out of sync with the file.
- **Prep notes are stored as Markdown source, never HTML.** The API saves
  and returns the source text; the frontend renders it with a small
  hand-rolled renderer (~130 lines, same reasoning as the SVG charts) that
  builds React elements from a safe subset — headings, lists, emphasis,
  code, blockquotes, http(s) links. Because user text only ever lands in
  React text nodes and `dangerouslySetInnerHTML` is never used, a prep note
  cannot become stored XSS, and there is no sanitizer dependency to keep
  current. The cost is a smaller Markdown dialect, which is the right trade
  for notes one person writes for themselves.
- **One origin in every environment.** In development the Vite server
  proxies `/api` to the backend; in the Compose stack nginx does the same
  job. The frontend always calls relative paths, so there is no CORS
  configuration to keep in sync and no API URL baked into the bundle at
  build time — the same image can run against any backend host.
- **Calendar export is generated, never stored.** An `.ics` file is a
  pure function of the rounds it covers, so it is built on request by a
  small hand-rolled RFC 5545 writer (TEXT escaping, CRLF endings,
  75-octet line folding) instead of a calendar dependency. Interviews
  store a start time but no end, so events export with a one-hour
  duration rather than a guessed per-round length. Event UIDs are
  stable (`interview-{id}@job-tracker`), so re-importing an updated
  export updates the existing events instead of duplicating them.
  Prep notes are deliberately excluded: they are a private working
  document, and calendar apps sync to other devices and accounts.
- **Reminders only fire for rounds that can still happen.** The
  upcoming list and the combined calendar export include SCHEDULED
  rounds only — a round that already happened, was cancelled, or ended
  in a no-show would otherwise keep reminding after it stopped mattering.

## Running it locally

### Docker Compose (full stack)

```bash
cp .env.example .env   # set a real JWT_SECRET before anything non-local
docker compose up --build
```

Open `http://localhost:5173`. Compose starts PostgreSQL (with a health
check the backend waits on), the API on port 8080, and the frontend served
by nginx on port 5173. nginx proxies `/api` to the backend, so the browser
only ever talks to one origin — no CORS setup and no build-time API URL.
Data survives restarts in the `db-data` volume; `docker compose down -v`
removes it. The manual setup below is the alternative for working on one
tier at a time.

### Backend (manual)

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

### Frontend (manual)

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

GitHub Actions runs both on every push and pull request, and also builds
the two Docker images so the Compose stack can't rot silently
(see `.github/workflows/ci.yml`).

## API overview

All endpoints except `/api/auth/*` require `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create an account, returns a JWT |
| POST | `/api/auth/login` | Log in, returns a JWT |
| GET | `/api/applications?status=APPLIED&q=acme&page=0&size=20` | List my applications, paged: `{content, page, size, totalElements, totalPages}`; optional status filter and case-insensitive search over company/title/location; `size` must be 1–100 |
| POST | `/api/applications` | Create an application |
| GET | `/api/applications/stale?days=10` | My stale applications: active stages with no update in at least `days` days (1–90, default 10), most stale first, each with `daysStale` |
| GET | `/api/applications/{id}` | Get one application |
| PUT | `/api/applications/{id}` | Update an application |
| PATCH | `/api/applications/{id}/status` | Move it through the pipeline |
| DELETE | `/api/applications/{id}` | Delete an application |
| GET | `/api/applications/export` | Download all my applications as CSV (the import format) |
| POST | `/api/applications/import` | Import a CSV file (multipart field `file`); all-or-nothing, max 1,000 rows, returns `{imported}` |
| GET | `/api/applications/{id}/interviews` | List interview rounds, including Markdown `prepNotes` |
| POST | `/api/applications/{id}/interviews` | Schedule an interview round |
| PUT | `/api/applications/{id}/interviews/{interviewId}` | Update a round (schedule, outcome, notes, prep notes; `prepNotes` capped at 20,000 chars) |
| GET | `/api/applications/{id}/interviews/{interviewId}/calendar.ics` | Download one round as an `.ics` file (1-hour event, reminders 24h and 1h before) |
| GET | `/api/interviews/upcoming?withinHours=48` | My SCHEDULED rounds starting within `withinHours` (1–720, default 48), soonest first, with company and title |
| GET | `/api/interviews/calendar.ics` | Download every future SCHEDULED round as one `.ics` file |
| GET | `/api/analytics/summary` | byStatus + responseRate + weekly, in one call |
| GET | `/api/analytics/by-status` | Counts per pipeline status |
| GET | `/api/analytics/weekly?weeks=8` | Applications per week |
| GET | `/api/analytics/response-rate` | Applied vs. responded and the rate |

## Roadmap (v2 ideas)

- Per-round duration instead of the fixed one-hour calendar default
- A weekly digest view: rounds held, outcomes recorded, follow-ups due

## License

MIT — see [LICENSE](LICENSE). Copyright © 2026 Sai Shekar Yeduru.
