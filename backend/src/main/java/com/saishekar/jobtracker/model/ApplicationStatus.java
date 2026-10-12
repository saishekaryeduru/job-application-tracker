package com.saishekar.jobtracker.model;

/**
 * The pipeline an application moves through. Order matters: analytics treats
 * every status after APPLIED as "the employer responded".
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
