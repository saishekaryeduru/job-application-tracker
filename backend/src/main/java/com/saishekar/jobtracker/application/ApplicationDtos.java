package com.saishekar.jobtracker.application;

import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.JobApplication;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplicationRequest(
            @NotBlank @Size(max = 160) String company,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 160) String location,
            @Size(max = 500) String jobUrl,
            ApplicationStatus status,
            @NotNull LocalDate appliedDate,
            @Min(0) Integer salaryMin,
            @Min(0) Integer salaryMax,
            String notes) {
    }

    public record ApplicationResponse(
            Long id,
            String company,
            String title,
            String location,
            String jobUrl,
            ApplicationStatus status,
            LocalDate appliedDate,
            Integer salaryMin,
            Integer salaryMax,
            String notes) {

        public static ApplicationResponse from(JobApplication app) {
            return new ApplicationResponse(
                    app.getId(), app.getCompany(), app.getTitle(), app.getLocation(),
                    app.getJobUrl(), app.getStatus(), app.getAppliedDate(),
                    app.getSalaryMin(), app.getSalaryMax(), app.getNotes());
        }
    }

    /** One page of the dashboard list, with the totals the pager needs. */
    public record ApplicationPage(
            List<ApplicationResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        public static ApplicationPage from(Page<JobApplication> page) {
            return new ApplicationPage(
                    page.getContent().stream().map(ApplicationResponse::from).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record StatusUpdateRequest(@NotNull ApplicationStatus status) {
    }

    public record CsvImportResult(int imported) {
    }

    /**
     * An application that has gone quiet: still in an active pipeline stage
     * but untouched for at least the requested number of days.
     */
    public record StaleApplicationResponse(
            Long id,
            String company,
            String title,
            String location,
            ApplicationStatus status,
            LocalDate appliedDate,
            Instant updatedAt,
            long daysStale) {

        public static StaleApplicationResponse from(JobApplication app, Instant now) {
            long daysStale = Duration.between(app.getUpdatedAt(), now).toDays();
            return new StaleApplicationResponse(
                    app.getId(), app.getCompany(), app.getTitle(), app.getLocation(),
                    app.getStatus(), app.getAppliedDate(), app.getUpdatedAt(), daysStale);
        }
    }
}
