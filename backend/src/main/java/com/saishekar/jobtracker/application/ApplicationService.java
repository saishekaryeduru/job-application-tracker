package com.saishekar.jobtracker.application;

import com.saishekar.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.saishekar.jobtracker.common.ApiException;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.JobApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationService {

    private final JobApplicationRepository applications;

    public ApplicationService(JobApplicationRepository applications) {
        this.applications = applications;
    }

    public static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public Page<JobApplication> list(User owner, ApplicationStatus status, String query,
                                       int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String trimmedQuery = query == null || query.isBlank() ? null : query.trim();
        // The id tie-breaker keeps the order stable when several applications
        // share an applied date, so rows cannot drift between pages.
        Sort sort = Sort.by(Sort.Direction.DESC, "appliedDate")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return applications.search(owner, status, trimmedQuery, PageRequest.of(page, size, sort));
    }

    @Transactional(readOnly = true)
    public JobApplication get(User owner, Long id) {
        return applications.findByIdAndOwner(id, owner)
                .orElseThrow(() -> ApiException.notFound("Application"));
    }

    public JobApplication create(User owner, ApplicationRequest request) {
        validateSalary(request);
        JobApplication app = new JobApplication(owner, request.company().trim(),
                request.title().trim(), request.appliedDate());
        apply(app, request);
        return applications.save(app);
    }

    public JobApplication update(User owner, Long id, ApplicationRequest request) {
        validateSalary(request);
        JobApplication app = get(owner, id);
        app.setCompany(request.company().trim());
        app.setTitle(request.title().trim());
        app.setAppliedDate(request.appliedDate());
        apply(app, request);
        app.touch();
        return applications.save(app);
    }

    public JobApplication updateStatus(User owner, Long id, ApplicationStatus status) {
        JobApplication app = get(owner, id);
        app.setStatus(status);
        app.touch();
        return applications.save(app);
    }

    public void delete(User owner, Long id) {
        applications.delete(get(owner, id));
    }

    private void apply(JobApplication app, ApplicationRequest request) {
        app.setLocation(blankToNull(request.location()));
        app.setJobUrl(blankToNull(request.jobUrl()));
        app.setStatus(request.status() == null ? ApplicationStatus.APPLIED : request.status());
        app.setSalaryMin(request.salaryMin());
        app.setSalaryMax(request.salaryMax());
        app.setNotes(request.notes());
    }

    private void validateSalary(ApplicationRequest request) {
        if (request.salaryMin() != null && request.salaryMax() != null
                && request.salaryMin() > request.salaryMax()) {
            throw ApiException.badRequest("salaryMin must not exceed salaryMax");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
