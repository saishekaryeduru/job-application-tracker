package com.saishekar.jobtracker.model;

/**
 * The pipeline an application moves through. SAVED (not yet applied) and
 * WITHDRAWN are excluded from the response-rate math in AnalyticsService.
 */
public enum ApplicationStatus {
    SAVED,
    APPLIED,
    SCREENING,
    INTERVIEWING,
    OFFER,
    REJECTED,
    WITHDRAWN
}
