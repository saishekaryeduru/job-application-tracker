package com.saishekar.jobtracker.interview;

import com.saishekar.jobtracker.interview.InterviewDtos.UpcomingInterviewResponse;
import com.saishekar.jobtracker.security.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Cross-application interview views: what is coming up, and the calendar
 * file covering all of it. Kept apart from InterviewController because
 * these routes are not nested under one application.
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewScheduleController {

    private static final MediaType TEXT_CALENDAR =
            new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final InterviewService service;
    private final InterviewCalendarService calendarService;
    private final CurrentUser currentUser;

    public InterviewScheduleController(InterviewService service,
                                       InterviewCalendarService calendarService,
                                       CurrentUser currentUser) {
        this.service = service;
        this.calendarService = calendarService;
        this.currentUser = currentUser;
    }

    @GetMapping("/upcoming")
    public List<UpcomingInterviewResponse> upcoming(
            @RequestParam(defaultValue = "" + InterviewService.DEFAULT_UPCOMING_HOURS)
            int withinHours) {
        return service.upcoming(currentUser.require(), withinHours).stream()
                .map(UpcomingInterviewResponse::from)
                .toList();
    }

    /** Every future SCHEDULED round in one .ics file, for a one-time calendar import. */
    @GetMapping("/calendar.ics")
    public ResponseEntity<byte[]> calendar() {
        String calendar = calendarService.calendarForAll(
                service.allUpcoming(currentUser.require()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CALENDAR);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("job-tracker-interviews.ics", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers)
                .body(calendar.getBytes(StandardCharsets.UTF_8));
    }
}
