-- Per-round duration: rounds are not all one hour (a recruiter screen is
-- typically 30 minutes, an onsite block runs several hours). Existing
-- rows keep the one-hour length the calendar export previously assumed.
ALTER TABLE interviews ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 60;
