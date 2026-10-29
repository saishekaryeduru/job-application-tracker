package com.saishekar.jobtracker.digest;

import com.saishekar.jobtracker.analytics.AnalyticsService;
import com.saishekar.jobtracker.application.ApplicationDtos;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyDigestEmailServiceTest {

    /** Records every message instead of sending it. */
    private static final class CapturingSender implements DigestMailSender {
        record Message(String to, String subject, String body) {
        }

        final List<Message> messages = new ArrayList<>();
        String failFor;

        @Override
        public void send(String to, String subject, String body) {
            if (to.equals(failFor)) {
                throw new IllegalStateException("SMTP unavailable");
            }
            messages.add(new Message(to, subject, body));
        }
    }

    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CapturingSender sender = new CapturingSender();
    private final WeeklyDigestEmailService service =
            new WeeklyDigestEmailService(analytics, users, sender);

    private final User ada = new User("ada@example.com", "hash", "Ada");
    private final User grace = new User("grace@example.com", "hash", "Grace");

    // The week of Monday 2026-10-19 to Sunday 2026-10-25.
    private final LocalDate weekStart = LocalDate.of(2026, 10, 19);
    private final LocalDate weekEnd = LocalDate.of(2026, 10, 25);

    @BeforeEach
    void stubDigest() {
        when(analytics.weeklyDigest(any(User.class), any(LocalDate.class)))
                .thenAnswer(invocation -> digestFor(invocation.getArgument(0)));
    }

    private AnalyticsService.WeeklyDigest digestFor(User owner) {
        Map<Interview.Outcome, Long> outcomes = new EnumMap<>(Interview.Outcome.class);
        for (Interview.Outcome outcome : Interview.Outcome.values()) {
            outcomes.put(outcome, 0L);
        }
        if (owner == ada) {
            outcomes.put(Interview.Outcome.PASSED, 1L);
            return new AnalyticsService.WeeklyDigest(weekStart, weekEnd,
                    List.of(new AnalyticsService.DigestApplication(
                            1L, "Acme", "Backend Engineer", ApplicationStatus.APPLIED,
                            LocalDate.of(2026, 10, 21))),
                    List.of(new AnalyticsService.DigestInterview(
                            2L, 1L, "Acme", "Backend Engineer", "Technical screen",
                            Instant.parse("2026-10-22T15:00:00Z"), Interview.Outcome.PASSED)),
                    outcomes,
                    List.of(new ApplicationDtos.StaleApplicationResponse(
                            3L, "Globex", "Platform Engineer", null, ApplicationStatus.APPLIED,
                            LocalDate.of(2026, 9, 30), Instant.parse("2026-10-05T15:00:00Z"), 19)),
                    1, 1, 1);
        }
        return new AnalyticsService.WeeklyDigest(weekStart, weekEnd,
                List.of(), List.of(), outcomes, List.of(), 0, 0, 0);
    }

    @Test
    void rendersTheDigestAsMail() {
        service.sendDigest(ada, weekStart);

        assertEquals(1, sender.messages.size());
        var message = sender.messages.get(0);
        assertEquals("ada@example.com", message.to());
        assertEquals("Your job search week: Oct 19 - Oct 25, 2026", message.subject());
        assertTrue(message.body().contains("Hi Ada,"));
        assertTrue(message.body().contains("Applications submitted: 1"));
        assertTrue(message.body().contains("Acme - Backend Engineer (APPLIED, applied Oct 21)"));
        assertTrue(message.body().contains("Interview rounds held: 1"));
        assertTrue(message.body().contains("Technical screen: PASSED"));
        assertTrue(message.body().contains("Outcomes recorded: PASSED 1"));
        assertTrue(message.body().contains("Follow-ups due now (no movement in 10+ days): 1"));
        assertTrue(message.body().contains("Globex - Platform Engineer (APPLIED, 19 days without movement)"));
    }

    @Test
    void emptyWeekReadsAsAnEmptyWeekNotABlankEmail() {
        service.sendDigest(grace, weekStart);

        var message = sender.messages.get(0);
        assertTrue(message.body().contains("Applications submitted: 0"));
        assertTrue(message.body().contains("None this week."));
        assertTrue(message.body().contains("None. Nothing has gone quiet."));
    }

    @Test
    void lastWeeksRunMailsEveryUserForThePreviousWeek() {
        when(users.findAll()).thenReturn(List.of(ada, grace));

        // Monday 2026-10-26: the digest sent is the week that just ended.
        int sent = service.sendLastWeeksDigests(LocalDate.of(2026, 10, 26));

        assertEquals(2, sent);
        assertEquals(List.of("ada@example.com", "grace@example.com"),
                sender.messages.stream().map(CapturingSender.Message::to).toList());
    }

    @Test
    void oneFailedSendDoesNotStopTheRest() {
        when(users.findAll()).thenReturn(List.of(ada, grace));
        sender.failFor = "ada@example.com";

        int sent = service.sendLastWeeksDigests(LocalDate.of(2026, 10, 26));

        assertEquals(1, sent);
        assertEquals("grace@example.com", sender.messages.get(0).to());
    }

    @Test
    void asksAnalyticsForTheWeekBeforeToday() {
        when(users.findAll()).thenReturn(List.of(ada));
        var askedFor = new ArrayList<LocalDate>();
        when(analytics.weeklyDigest(eq(ada), any(LocalDate.class))).thenAnswer(invocation -> {
            askedFor.add(invocation.getArgument(1));
            return digestFor(ada);
        });

        service.sendLastWeeksDigests(LocalDate.of(2026, 10, 26));

        // Any date in the previous week selects it; the service passes the
        // Monday seven days back rather than today's date.
        assertEquals(List.of(LocalDate.of(2026, 10, 19)), askedFor);
    }
}
