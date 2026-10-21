package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Builds RFC 5545 iCalendar documents for scheduled interview rounds.
 *
 * The format is a handful of text properties, so — like the CSV module —
 * this is hand-rolled standard-library code rather than a calendar
 * dependency. The parts that are easy to get wrong are handled here and
 * nowhere else: TEXT escaping, CRLF line endings, and folding lines at
 * 75 octets.
 *
 * Two modelling choices are deliberate:
 * - The event end is the stored start plus the round's own duration.
 *   Rounds created before durations were recorded carry the previous
 *   one-hour default, so their exports are unchanged.
 * - Prep notes are never exported. They are a private working document
 *   (stories to rehearse, weaknesses to shore up), not calendar data,
 *   and calendar apps sync to other devices and accounts.
 */
@Service
public class InterviewCalendarService {

    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** RFC 5545 section 3.1: content lines should not exceed 75 octets. */
    private static final int MAX_LINE_OCTETS = 75;

    public String calendarFor(Interview interview) {
        return calendarForAll(List.of(interview));
    }

    public String calendarForAll(List<Interview> interviews) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Job Tracker//Interview Calendar//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:Job Tracker Interviews");
        Instant stamp = Instant.now();
        for (Interview interview : interviews) {
            appendEvent(out, interview, stamp);
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private void appendEvent(StringBuilder out, Interview interview, Instant stamp) {
        JobApplication app = interview.getApplication();
        Instant start = interview.getScheduledAt();
        Instant end = start.plus(interview.getDurationMinutes(), ChronoUnit.MINUTES);

        line(out, "BEGIN:VEVENT");
        // The id is database-unique and never reused, so the UID stays
        // stable across re-exports: importing twice updates one event
        // instead of duplicating it.
        line(out, "UID:interview-" + interview.getId() + "@job-tracker");
        line(out, "DTSTAMP:" + UTC_FORMAT.format(stamp));
        line(out, "DTSTART:" + UTC_FORMAT.format(start));
        line(out, "DTEND:" + UTC_FORMAT.format(end));
        line(out, "SUMMARY:" + escape(interview.getRound() + " - "
                + app.getTitle() + " at " + app.getCompany()));
        line(out, "DESCRIPTION:" + escape(description(interview, app)));
        if (app.getLocation() != null && !app.getLocation().isBlank()) {
            line(out, "LOCATION:" + escape(app.getLocation()));
        }
        if (app.getJobUrl() != null && !app.getJobUrl().isBlank()) {
            line(out, "URL:" + app.getJobUrl());
        }
        appendAlarm(out, "-P1D", "Interview tomorrow: " + interview.getRound()
                + " at " + app.getCompany());
        appendAlarm(out, "-PT1H", "Interview in one hour: " + interview.getRound()
                + " at " + app.getCompany());
        line(out, "END:VEVENT");
    }

    private void appendAlarm(StringBuilder out, String trigger, String description) {
        line(out, "BEGIN:VALARM");
        line(out, "ACTION:DISPLAY");
        line(out, "TRIGGER:" + trigger);
        line(out, "DESCRIPTION:" + escape(description));
        line(out, "END:VALARM");
    }

    private String description(Interview interview, JobApplication app) {
        StringBuilder text = new StringBuilder()
                .append(interview.getRound()).append(" for ")
                .append(app.getTitle()).append(" at ").append(app.getCompany()).append('.');
        if (interview.getInterviewer() != null && !interview.getInterviewer().isBlank()) {
            text.append("\nInterviewer: ").append(interview.getInterviewer());
        }
        if (interview.getNotes() != null && !interview.getNotes().isBlank()) {
            text.append("\nNotes: ").append(interview.getNotes());
        }
        if (app.getJobUrl() != null && !app.getJobUrl().isBlank()) {
            text.append("\nPosting: ").append(app.getJobUrl());
        }
        return text.toString();
    }

    /** RFC 5545 section 3.3.11 TEXT escaping. Order matters: backslash first. */
    static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Appends one logical line, folded if needed, terminated by CRLF.
     * Folding inserts CRLF + one space; the space counts toward the next
     * segment's budget. Length is measured in UTF-8 octets, not chars,
     * because the 75-octet limit is about the bytes on the wire.
     */
    private void line(StringBuilder out, String logicalLine) {
        String remaining = logicalLine;
        int budget = MAX_LINE_OCTETS;
        while (true) {
            int octets = remaining.getBytes(StandardCharsets.UTF_8).length;
            if (octets <= budget) {
                out.append(remaining).append("\r\n");
                return;
            }
            int cut = cutIndex(remaining, budget);
            out.append(remaining, 0, cut).append("\r\n ");
            remaining = remaining.substring(cut);
            // Continuation lines spend one octet on the leading space.
            budget = MAX_LINE_OCTETS - 1;
        }
    }

    /** Largest prefix of {@code text} that fits in {@code octetBudget} without splitting a code point. */
    private int cutIndex(String text, int octetBudget) {
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int width = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (used + width > octetBudget) {
                break;
            }
            used += width;
            index += Character.charCount(codePoint);
        }
        // A single code point always fits in a 74+ octet budget, so the
        // loop above can only return 0 for an empty string, which the
        // caller never passes here. Guard anyway to rule out an infinite loop.
        return Math.max(index, 1);
    }
}
