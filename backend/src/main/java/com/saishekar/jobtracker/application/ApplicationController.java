package com.saishekar.jobtracker.application;

import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.saishekar.jobtracker.application.ApplicationDtos.StatusUpdateRequest;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final CurrentUser currentUser;

    public ApplicationController(ApplicationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ApplicationResponse> list(
            @RequestParam(required = false) ApplicationStatus status) {
        return service.list(currentUser.require(), status).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return ApplicationResponse.from(service.get(currentUser.require(), id));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        var created = service.create(currentUser.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody ApplicationRequest request) {
        return ApplicationResponse.from(service.update(currentUser.require(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return ApplicationResponse.from(
                service.updateStatus(currentUser.require(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}
