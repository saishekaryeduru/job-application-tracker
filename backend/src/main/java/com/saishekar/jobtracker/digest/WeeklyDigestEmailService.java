package com.saishekar.jobtracker.digest;

import com.saishekar.jobtracker.analytics.AnalyticsService;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders the weekly digest as a plain-text email and sends it. The content
 * comes from the same {@link AnalyticsService.WeeklyDigest} the analytics
 * page shows, so the email can never disagree with the screen; this class
 * only decides how that digest reads as mail.
 */
@Service
public class WeeklyDigestEmailService {

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyDigestEmailService.class);

    private static final DateTimeFormatter SUBJECT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final AnalyticsService analytics;
    private final UserRepository users;
    private final DigestMailSender mailSender;

    public WeeklyDigestEmailService(AnalyticsService analytics, UserRepository users,
                                    DigestMailSender mailSender) {
        this.analytics = analytics;
        this.users = users;
        this.mailSender = mailSender;
    }

    /** Sends one user the digest for the week containing {@code weekDate}. */
    public void sendDigest(User owner, LocalDate weekDate) {
        AnalyticsService.WeeklyDigest digest = analytics.weeklyDigest(owner, weekDate);
        mailSender.send(owner.getEmail(), subject(digest), body(owner, digest));
    }

    /**
     * Sends every registered user the digest for the week that ended before
     * {@code today} (today minus one week selects that week, because the
     * digest snaps any date to its Monday). One user's failure — a bad
     * address, an SMTP error — is logged and skipped so it cannot stop the
     * remaining users from getting theirs. Returns the number of digests
     * actually sent.
     */
    // Deliberately not transactional: a send can block on SMTP, and no
    // database work here spans more than one repository call — each read
    // (the user list, the digest) runs in its own transaction.
    public int sendLastWeeksDigests(LocalDate today) {
        LocalDate lastWeek = today.minusWeeks(1);
        int sent = 0;
        for (User owner : users.findAll()) {
            try {
                sendDigest(owner, lastWeek);
                sent++;
            } catch (RuntimeException ex) {
                LOG.warn("Weekly digest to {} failed: {}", owner.getEmail(), ex.getMessage());
            }
        }
        LOG.info("Weekly digest run for the week containing {}: {} sent", lastWeek, sent);
        return sent;
    }

    static String subject(AnalyticsService.WeeklyDigest digest) {
        return "Your job search week: " + digest.weekStart().format(SUBJECT_DAY)
                + " - " + digest.weekEnd().format(SUBJECT_DAY)
                + ", " + digest.weekEnd().getYear();
    }

    static String body(User owner, AnalyticsService.WeeklyDigest digest) {
        var text = new StringBuilder();
        text.append("Hi ").append(owner.getDisplayName()).append(",\n\n");
        text.append("Here is your job search for the week of ")
                .append(digest.weekStart().format(LONG_DAY))
                .append(" to ")
                .append(digest.weekEnd().format(LONG_DAY))
                .append(", ").append(digest.weekEnd().getYear()).append(".\n\n");

        text.append("Applications submitted: ").append(digest.applicationsSubmitted()).append('\n');
        if (digest.submitted().isEmpty()) {
            text.append("  None this week.\n");
        }
        for (var app : digest.submitted()) {
            text.append("  - ").append(app.company()).append(" - ").append(app.title())
                    .append(" (").append(app.status())
                    .append(", applied ").append(app.appliedDate().format(SHORT_DAY)).append(")\n");
        }
        text.append('\n');

        text.append("Interview rounds held: ").append(digest.roundsHeldCount()).append('\n');
        if (digest.roundsHeld().isEmpty()) {
            text.append("  None this week.\n");
        }
        for (var round : digest.roundsHeld()) {
            text.append("  - ").append(round.company()).append(" - ").append(round.title())
                    .append(", ").append(round.round()).append(": ").append(round.outcome()).append('\n');
        }
        var outcomes = new StringBuilder();
        digest.outcomes().forEach((outcome, count) -> {
            if (count > 0) {
                if (!outcomes.isEmpty()) {
                    outcomes.append(", ");
                }
                outcomes.append(outcome).append(' ').append(count);
            }
        });
        if (!outcomes.isEmpty()) {
            text.append("  Outcomes recorded: ").append(outcomes).append('\n');
        }
        text.append('\n');

        // Follow-ups are a current snapshot, not a record of the week that
        // ended (see AnalyticsService.weeklyDigest) — the heading says so,
        // so nobody reads today's quiet list as last week's.
        text.append("Follow-ups due now (no movement in ")
                .append(AnalyticsService.FOLLOW_UP_DAYS).append("+ days): ")
                .append(digest.followUpsDueCount()).append('\n');
        if (digest.followUpsDue().isEmpty()) {
            text.append("  None. Nothing has gone quiet.\n");
        }
        for (var app : digest.followUpsDue()) {
            text.append("  - ").append(app.company()).append(" - ").append(app.title())
                    .append(" (").append(app.status())
                    .append(", ").append(app.daysStale()).append(" days without movement)\n");
        }
        text.append('\n');
        text.append("The full digest, with charts, is on the Analytics page in Job Tracker.\n");
        return text.toString();
    }
}
