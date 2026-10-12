package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.model.Interview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class InterviewDtos {

    private InterviewDtos() {
    }

    public record InterviewRequest(
            @NotBlank @Size(max = 80) String round,
            @NotNull Instant scheduledAt,
            @Size(max = 160) String interviewer,
            Interview.Outcome outcome,
            String notes) {
    }

    public record InterviewResponse(
            Long id,
            Long applicationId,
            String round,
            Instant scheduledAt,
            String interviewer,
            Interview.Outcome outcome,
            String notes) {

        public static InterviewResponse from(Interview interview) {
            return new InterviewResponse(
                    interview.getId(), interview.getApplication().getId(),
                    interview.getRound(), interview.getScheduledAt(),
                    interview.getInterviewer(), interview.getOutcome(), interview.getNotes());
        }
    }
}
