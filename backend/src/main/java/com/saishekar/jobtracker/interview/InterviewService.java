package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.application.ApplicationService;
import com.saishekar.jobtracker.common.ApiException;
import com.saishekar.jobtracker.interview.InterviewDtos.InterviewRequest;
import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class InterviewService {

    public static final int DEFAULT_UPCOMING_HOURS = 48;
    public static final int MAX_UPCOMING_HOURS = 720;

    private final ApplicationService applications;
    private final InterviewRepository interviews;

    public InterviewService(ApplicationService applications, InterviewRepository interviews) {
        this.applications = applications;
        this.interviews = interviews;
    }

    @Transactional(readOnly = true)
    public List<Interview> list(User owner, Long applicationId) {
        JobApplication app = applications.get(owner, applicationId);
        return interviews.findByApplicationOrderByScheduledAtAsc(app);
    }

    @Transactional(readOnly = true)
    public Interview get(User owner, Long applicationId, Long interviewId) {
        JobApplication app = applications.get(owner, applicationId);
        return interviews.findByIdAndApplication(interviewId, app)
                .orElseThrow(() -> ApiException.notFound("Interview"));
    }

    /**
     * SCHEDULED rounds starting within the next {@code withinHours} hours,
     * soonest first, across all of the owner's applications. The window is
     * capped at 30 days: beyond that the list stops being a reminder and
     * becomes a second copy of the dashboard.
     */
    @Transactional(readOnly = true)
    public List<Interview> upcoming(User owner, int withinHours) {
        if (withinHours < 1 || withinHours > MAX_UPCOMING_HOURS) {
            throw ApiException.badRequest(
                    "withinHours must be between 1 and " + MAX_UPCOMING_HOURS);
        }
        Instant now = Instant.now();
        return interviews.findUpcoming(owner, Interview.Outcome.SCHEDULED,
                now, now.plus(withinHours, ChronoUnit.HOURS));
    }

    /** Every future SCHEDULED round for the owner, for the calendar export. */
    @Transactional(readOnly = true)
    public List<Interview> allUpcoming(User owner) {
        return interviews.findAllUpcoming(owner, Interview.Outcome.SCHEDULED, Instant.now());
    }

    public Interview create(User owner, Long applicationId, InterviewRequest request) {
        JobApplication app = applications.get(owner, applicationId);
        Interview interview = new Interview(app, request.round().trim(), request.scheduledAt());
        apply(interview, request);
        return interviews.save(interview);
    }

    public Interview update(User owner, Long applicationId, Long interviewId,
                            InterviewRequest request) {
        // get() resolves the application through the owner first, which is
        // what keeps one user from updating another user's interview by
        // guessing its id: the interview lookup is scoped to that
        // already-authorized parent.
        Interview interview = get(owner, applicationId, interviewId);
        interview.setRound(request.round().trim());
        interview.setScheduledAt(request.scheduledAt());
        apply(interview, request);
        return interviews.save(interview);
    }

    private void apply(Interview interview, InterviewRequest request) {
        interview.setInterviewer(request.interviewer());
        // A null outcome means "not stated": on create the entity default
        // (SCHEDULED) stands, on update the recorded outcome is kept rather
        // than silently reset by a client that only meant to edit prep notes.
        if (request.outcome() != null) {
            interview.setOutcome(request.outcome());
        }
        interview.setNotes(request.notes());
        interview.setPrepNotes(request.prepNotes());
    }
}
