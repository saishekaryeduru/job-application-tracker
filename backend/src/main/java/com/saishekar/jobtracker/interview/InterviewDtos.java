package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.model.Interview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class InterviewDtos {

    private InterviewDtos() {
    }

    /** Prep notes are a working document, not a tweet: 20k chars is room for a full prep sheet. */
    public static final int MAX_PREP_NOTES_LENGTH = 20_000;

    /** Shortest round worth scheduling; anything under 15 minutes is a data-entry slip. */
    public static final int MIN_DURATION_MINUTES = 15;

    /** Longest single block: a full-day onsite is eight hours, and longer than that is two rounds. */
    public static final int MAX_DURATION_MINUTES = 480;

    public record InterviewRequest(
            @NotBlank @Size(max = 80) String round,
            @NotNull Instant scheduledAt,
            @Min(MIN_DURATION_MINUTES) @Max(MAX_DURATION_MINUTES) Integer durationMinutes,
            @Size(max = 160) String interviewer,
            Interview.Outcome outcome,
            String notes,
            @Size(max = MAX_PREP_NOTES_LENGTH) String prepNotes) {
    }

    public record InterviewResponse(
            Long id,
            Long applicationId,
            String round,
            Instant scheduledAt,
            int durationMinutes,
            String interviewer,
            Interview.Outcome outcome,
            String notes,
            String prepNotes) {

        public static InterviewResponse from(Interview interview) {
            return new InterviewResponse(
                    interview.getId(), interview.getApplication().getId(),
                    interview.getRound(), interview.getScheduledAt(),
                    interview.getDurationMinutes(),
                    interview.getInterviewer(), interview.getOutcome(), interview.getNotes(),
                    interview.getPrepNotes());
        }
    }

    /**
     * One row of the cross-application upcoming list. Carries the parent
     * application's company and title because a reminder that only says
     * "Technical deep dive at 3pm" is not actionable on its own.
     */
    public record UpcomingInterviewResponse(
            Long id,
            Long applicationId,
            String company,
            String title,
            String round,
            Instant scheduledAt,
            int durationMinutes,
            String interviewer) {

        public static UpcomingInterviewResponse from(Interview interview) {
            var app = interview.getApplication();
            return new UpcomingInterviewResponse(
                    interview.getId(), app.getId(),
                    app.getCompany(), app.getTitle(),
                    interview.getRound(), interview.getScheduledAt(),
                    interview.getDurationMinutes(),
                    interview.getInterviewer());
        }
    }
}
