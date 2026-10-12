package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.application.ApplicationService;
import com.saishekar.jobtracker.interview.InterviewDtos.InterviewRequest;
import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InterviewService {

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

    public Interview create(User owner, Long applicationId, InterviewRequest request) {
        JobApplication app = applications.get(owner, applicationId);
        Interview interview = new Interview(app, request.round().trim(), request.scheduledAt());
        interview.setInterviewer(request.interviewer());
        if (request.outcome() != null) {
            interview.setOutcome(request.outcome());
        }
        interview.setNotes(request.notes());
        return interviews.save(interview);
    }
}
