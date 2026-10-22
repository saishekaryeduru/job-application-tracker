package com.saishekar.jobtracker.analytics;

import com.saishekar.jobtracker.analytics.AnalyticsService.ResponseRate;
import com.saishekar.jobtracker.analytics.AnalyticsService.WeeklyCount;
import com.saishekar.jobtracker.analytics.AnalyticsService.WeeklyDigest;
import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.User;
import com.saishekar.jobtracker.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService analytics, CurrentUser currentUser) {
        this.analytics = analytics;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        User owner = currentUser.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("byStatus", analytics.countByStatus(owner));
        payload.put("responseRate", analytics.responseRate(owner));
        payload.put("weekly", analytics.weeklyApplications(owner, 8));
        return payload;
    }

    @GetMapping("/by-status")
    public Map<ApplicationStatus, Long> byStatus() {
        return analytics.countByStatus(currentUser.require());
    }

    @GetMapping("/weekly")
    public List<WeeklyCount> weekly(@RequestParam(defaultValue = "8") int weeks) {
        int bounded = Math.max(1, Math.min(weeks, 52));
        return analytics.weeklyApplications(currentUser.require(), bounded);
    }

    @GetMapping("/response-rate")
    public ResponseRate responseRate() {
        return analytics.responseRate(currentUser.require());
    }

    @GetMapping("/weekly-digest")
    public WeeklyDigest weeklyDigest() {
        return analytics.weeklyDigest(currentUser.require());
    }
}
