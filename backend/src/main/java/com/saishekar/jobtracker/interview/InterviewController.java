package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.interview.InterviewDtos.InterviewRequest;
import com.saishekar.jobtracker.interview.InterviewDtos.InterviewResponse;
import com.saishekar.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/interviews")
public class InterviewController {

    private final InterviewService service;
    private final CurrentUser currentUser;

    public InterviewController(InterviewService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<InterviewResponse> list(@PathVariable Long applicationId) {
        return service.list(currentUser.require(), applicationId).stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<InterviewResponse> create(@PathVariable Long applicationId,
                                                    @Valid @RequestBody InterviewRequest request) {
        var created = service.create(currentUser.require(), applicationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(InterviewResponse.from(created));
    }

    @PutMapping("/{interviewId}")
    public InterviewResponse update(@PathVariable Long applicationId,
                                    @PathVariable Long interviewId,
                                    @Valid @RequestBody InterviewRequest request) {
        var updated = service.update(currentUser.require(), applicationId, interviewId, request);
        return InterviewResponse.from(updated);
    }
}
