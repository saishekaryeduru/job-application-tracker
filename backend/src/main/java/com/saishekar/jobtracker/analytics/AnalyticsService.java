package com.saishekar.jobtracker.analytics;

import com.saishekar.jobtracker.application.ApplicationDtos;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.InterviewRepository;
import com.saishekar.jobtracker.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline counts, applications per week, response rate, and the weekly
 * digest. Everything is computed from the applications and interviews
 * tables at request time — no cached counters to drift out of sync.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Statuses that mean "the employer got back to us" in some form. */
    private static final List<ApplicationStatus> RESPONDED = List.of(
            ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEWING,
            ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

    /**
     * Stages where silence is worth a follow-up. Mirrors the stale-reminder
     * rule in ApplicationService: SAVED has not been submitted, and OFFER,
     * REJECTED, and WITHDRAWN are closed.
     */
    private static final List<ApplicationStatus> FOLLOW_UP_STATUSES = List.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.SCREENING,
            ApplicationStatus.INTERVIEWING);

    /** Outcomes where the round actually took place; CANCELLED and SCHEDULED did not. */
    private static final List<Interview.Outcome> HELD_OUTCOMES = List.of(
            Interview.Outcome.PASSED, Interview.Outcome.FAILED, Interview.Outcome.NO_SHOW);

    /** Days without movement before the digest lists an application as a follow-up. */
    public static final int FOLLOW_UP_DAYS = 10;

    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;

    public AnalyticsService(JobApplicationRepository applications, InterviewRepository interviews) {
        this.applications = applications;
        this.interviews = interviews;
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

    /**
     * One week at a glance, for the Monday-to-Sunday week containing today:
     * what was submitted, which rounds were held and how they ended, and
     * which active applications have gone quiet enough to need a follow-up.
     * The week window for interviews is the same Monday boundary the weekly
     * counts use, expressed as a half-open UTC instant range so a round at
     * exactly midnight on Monday belongs to exactly one week.
     */
    public WeeklyDigest weeklyDigest(User owner) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        Instant weekFrom = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekTo = weekStart.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<DigestApplication> submitted = applications
                .findByOwnerAndAppliedDateBetweenOrderByAppliedDateDescIdDesc(owner, weekStart, weekEnd)
                .stream()
                .map(app -> new DigestApplication(app.getId(), app.getCompany(), app.getTitle(),
                        app.getStatus(), app.getAppliedDate()))
                .toList();

        List<Interview> inWeek = interviews.findByOwnerAndScheduledAtBetween(owner, weekFrom, weekTo);
        Map<Interview.Outcome, Long> outcomes = new EnumMap<>(Interview.Outcome.class);
        for (Interview.Outcome outcome : Interview.Outcome.values()) {
            outcomes.put(outcome, 0L);
        }
        for (Interview interview : inWeek) {
            outcomes.merge(interview.getOutcome(), 1L, Long::sum);
        }
        List<DigestInterview> roundsHeld = inWeek.stream()
                .filter(interview -> HELD_OUTCOMES.contains(interview.getOutcome()))
                .map(interview -> new DigestInterview(interview.getId(),
                        interview.getApplication().getId(),
                        interview.getApplication().getCompany(),
                        interview.getApplication().getTitle(),
                        interview.getRound(), interview.getScheduledAt(), interview.getOutcome()))
                .toList();

        Instant now = Instant.now();
        Instant followUpCutoff = now.minus(FOLLOW_UP_DAYS, ChronoUnit.DAYS);
        List<ApplicationDtos.StaleApplicationResponse> followUpsDue = applications
                .findByOwnerAndStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        owner, FOLLOW_UP_STATUSES, followUpCutoff)
                .stream()
                .map(app -> ApplicationDtos.StaleApplicationResponse.from(app, now))
                .toList();

        return new WeeklyDigest(weekStart, weekEnd, submitted, roundsHeld, outcomes, followUpsDue,
                submitted.size(), roundsHeld.size(), followUpsDue.size());
    }

    public record WeeklyCount(LocalDate weekStart, long count) {
    }

    public record ResponseRate(long applied, long responded, double rate) {
    }

    public record DigestApplication(Long id, String company, String title,
                                    ApplicationStatus status, LocalDate appliedDate) {
    }

    public record DigestInterview(Long id, Long applicationId, String company, String title,
                                  String round, Instant scheduledAt, Interview.Outcome outcome) {
    }

    public record WeeklyDigest(LocalDate weekStart, LocalDate weekEnd,
                               List<DigestApplication> submitted,
                               List<DigestInterview> roundsHeld,
                               Map<Interview.Outcome, Long> outcomes,
                               List<ApplicationDtos.StaleApplicationResponse> followUpsDue,
                               long applicationsSubmitted,
                               long roundsHeldCount,
                               long followUpsDueCount) {
    }
}
