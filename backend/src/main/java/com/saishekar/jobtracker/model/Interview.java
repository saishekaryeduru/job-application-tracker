package com.saishekar.jobtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "interviews")
public class Interview {

    public enum Outcome {
        SCHEDULED, PASSED, FAILED, CANCELLED, NO_SHOW
    }

    /** Length of a round in minutes when none is stated; the previous fixed calendar default. */
    public static final int DEFAULT_DURATION_MINUTES = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    @Column(nullable = false)
    private String round;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    /** Length of the round in minutes; recruiter screens and onsite blocks are not the same size. */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = DEFAULT_DURATION_MINUTES;

    private String interviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome = Outcome.SCHEDULED;

    private String notes;

    /**
     * Markdown the candidate writes while preparing for the round. Stored as
     * source and rendered client-side; the API never returns HTML, so stored
     * markup cannot become stored XSS.
     */
    @Column(name = "prep_notes", columnDefinition = "TEXT")
    private String prepNotes;

    protected Interview() {
    }

    public Interview(JobApplication application, String round, Instant scheduledAt) {
        this.application = application;
        this.round = round;
        this.scheduledAt = scheduledAt;
    }

    public Long getId() {
        return id;
    }

    public JobApplication getApplication() {
        return application;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getInterviewer() {
        return interviewer;
    }

    public void setInterviewer(String interviewer) {
        this.interviewer = interviewer;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPrepNotes() {
        return prepNotes;
    }

    public void setPrepNotes(String prepNotes) {
        this.prepNotes = prepNotes;
    }
}
