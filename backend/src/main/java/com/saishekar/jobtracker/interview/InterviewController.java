package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.application.ApplicationService;
import com.saishekar.jobtracker.interview.InterviewDtos.InterviewRequest;
import com.saishekar.jobtracker.interview.InterviewDtos.InterviewResponse;
import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.repository.InterviewRepository;
import com.saishekar.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Interviews are nested under the application they belong to. */
@RestController
@RequestMapping("/api/applications/{applicationId}/interviews")
@Transactional
public class InterviewController {

    private final ApplicationService applications;
    private final InterviewRepository interviews;
    private final CurrentUser currentUser;

    public InterviewController(ApplicationService applications, InterviewRepository interviews,
                               CurrentUser currentUser) {
        this.applications = applications;
        this.interviews = interviews;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<InterviewResponse> list(@PathVariable Long applicationId) {
        JobApplication app = applications.get(currentUser.require(), applicationId);
        return interviews.findByApplicationOrderByScheduledAtAsc(app).stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<InterviewResponse> create(@PathVariable Long applicationId,
                                                      @Valid @RequestBody InterviewRequest request) {
        JobApplication app = applications.get(currentUser.require(), applicationId);
        Interview interview = new Interview(app, request.round().trim(), request.scheduledAt());
        interview.setInterviewer(request.interviewer());
        if (request.outcome() != null) {
            interview.setOutcome(request.outcome());
        }
        interview.setNotes(request.notes());
        Interview saved = interviews.save(interview);
        return ResponseEntity.status(HttpStatus.CREATED).body(InterviewResponse.from(saved));
    }
}
