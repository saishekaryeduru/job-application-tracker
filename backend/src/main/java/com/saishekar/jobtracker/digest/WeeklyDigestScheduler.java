package com.saishekar.jobtracker.digest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Monday 08:00 UTC: mail every user the digest for the week that just
 * ended. The job exists only when the deployment opts in with
 * {@code app.digest.email.enabled=true} — a deployment without a mail
 * server must not wake up every Monday and fail for every user.
 */
@Component
@ConditionalOnProperty(name = "app.digest.email.enabled", havingValue = "true")
public class WeeklyDigestScheduler {

    private final WeeklyDigestEmailService service;

    public WeeklyDigestScheduler(WeeklyDigestEmailService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "UTC")
    public void sendWeeklyDigests() {
        service.sendLastWeeksDigests(LocalDate.now());
    }
}
