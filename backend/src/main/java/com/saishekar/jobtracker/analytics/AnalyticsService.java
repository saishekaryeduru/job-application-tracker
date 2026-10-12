package com.saishekar.jobtracker.analytics;

import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline counts, applications per week, and response rate. Everything is
 * computed from the applications table at request time — no cached counters
 * to drift out of sync.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Statuses that mean "the employer got back to us" in some form. */
    private static final List<ApplicationStatus> RESPONDED = List.of(
            ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEWING,
            ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

    private final JobApplicationRepository applications;

    public AnalyticsService(JobApplicationRepository applications) {
        this.applications = applications;
    }

    public Map<ApplicationStatus, Long> countByStatus(User owner) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, applications.countByOwnerAndStatus(owner, status));
        }
        return counts;
    }

    public List<WeeklyCount> weeklyApplications(User owner, int weeks) {
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<WeeklyCount> result = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate weekStart = thisMonday.minusWeeks(i);
            LocalDate weekEnd = weekStart.plusDays(6);
            long count = applications.countByOwnerAndAppliedDateBetween(owner, weekStart, weekEnd);
            result.add(new WeeklyCount(weekStart, count));
        }
        return result;
    }

    public ResponseRate responseRate(User owner) {
        long applied = 0;
        long responded = 0;
        for (var entry : countByStatus(owner).entrySet()) {
            if (entry.getKey() == ApplicationStatus.SAVED
                    || entry.getKey() == ApplicationStatus.WITHDRAWN) {
                continue;
            }
            applied += entry.getValue();
            if (RESPONDED.contains(entry.getKey())) {
                responded += entry.getValue();
            }
        }
        double rate = applied == 0 ? 0.0 : (double) responded / applied;
        return new ResponseRate(applied, responded, rate);
    }

    public record WeeklyCount(LocalDate weekStart, long count) {
    }

    public record ResponseRate(long applied, long responded, double rate) {
    }
}
