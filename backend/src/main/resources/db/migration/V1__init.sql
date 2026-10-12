-- Job Tracker initial schema (PostgreSQL)

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE job_applications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company      VARCHAR(160) NOT NULL,
    title        VARCHAR(160) NOT NULL,
    location     VARCHAR(160),
    job_url      VARCHAR(500),
    status       VARCHAR(30) NOT NULL DEFAULT 'SAVED',
    applied_date DATE NOT NULL,
    salary_min   INTEGER,
    salary_max   INTEGER,
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_user ON job_applications (user_id);
CREATE INDEX idx_applications_status ON job_applications (status);
CREATE INDEX idx_applications_applied_date ON job_applications (applied_date);

CREATE TABLE interviews (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    round          VARCHAR(80) NOT NULL,
    scheduled_at   TIMESTAMP NOT NULL,
    interviewer    VARCHAR(160),
    outcome        VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    notes          TEXT
);

CREATE INDEX idx_interviews_application ON interviews (application_id);
