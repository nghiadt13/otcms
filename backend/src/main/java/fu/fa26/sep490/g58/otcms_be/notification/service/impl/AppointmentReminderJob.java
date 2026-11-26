package fu.fa26.sep490.g58.otcms_be.notification.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.NotificationRecipientDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReminderCandidateService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReminderCandidateService.Candidate;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NF-09 / MSG-80: reminds the patient 24 hours before a booked appointment or follow-up, and once when a
 * recommended follow-up has passed without the patient returning. Runs every 15 minutes.
 *
 * <p>No duplicates: each notification carries a {@code dedupe_key} (unique partial index, V133) and is inserted with
 * ON CONFLICT DO NOTHING, so overlapping runs or several app instances cannot double-send. The key of an upcoming
 * reminder includes the start time, so a rescheduled appointment is reminded again at its new time.
 *
 * <p>Delivery is the in-app notice only: the system has no generic email sender yet (known gap, see handoff).
 */
@Component
public class AppointmentReminderJob {
    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);
    /** Overdue follow-ups older than this are not announced, so a first deployment cannot flood old patients. */
    static final Duration OVERDUE_LOOKBACK = Duration.ofDays(30);

    /** @param reminders new upcoming reminders; @param overdue new overdue notices; @param skipped duplicates or no recipient */
    public record RunResult(int reminders, int overdue, int skipped) { }

    private final AppointmentReminderCandidateService candidates;
    private final NotificationRepository notifications;
    private final NotificationRecipientDirectory recipients;
    private final Clock clock;
    private final boolean enabled;
    private final Duration lead;

    public AppointmentReminderJob(AppointmentReminderCandidateService candidates, NotificationRepository notifications,
            NotificationRecipientDirectory recipients, Clock clock,
            @Value("${otcms.notification.reminder-job-enabled:true}") boolean enabled,
            @Value("${otcms.notification.reminder-lead-hours:24}") long leadHours) {
        this.candidates = candidates;
        this.notifications = notifications;
        this.recipients = recipients;
        this.clock = clock;
        this.enabled = enabled;
        this.lead = Duration.ofHours(leadHours);
    }

    @Scheduled(fixedDelayString = "${otcms.notification.reminder-poll-ms:900000}")
    public void scheduledRun() {
        if (!enabled) return;
        try {
            runOnce();
        } catch (RuntimeException failure) {
            log.error("Appointment reminder run failed", failure);
        }
    }

    /** One pass; safe to call repeatedly (tests do). */
    public RunResult runOnce() {
        Instant now = clock.instant();
        int reminders = 0, overdue = 0, skipped = 0;
        for (Candidate candidate : candidates.upcoming(now, now.plus(lead))) {
            String type = candidate.followUp() ? NotificationMessages.FOLLOW_UP_REMINDER : NotificationMessages.APPOINTMENT_REMINDER;
            String key = type + ":" + candidate.appointmentId() + ":" + candidate.startTime().getEpochSecond();
            NotificationMessage message = NotificationMessages.appointmentReminder(candidate.appointmentId(),
                    candidate.startTime(), candidate.patientName(), candidate.followUp());
            if (send(candidate, message, key, now)) reminders++; else skipped++;
        }
        for (Candidate candidate : candidates.overdueFollowUps(now, now.minus(OVERDUE_LOOKBACK))) {
            String key = NotificationMessages.FOLLOW_UP_OVERDUE + ":" + candidate.appointmentId();
            NotificationMessage message = NotificationMessages.followUpOverdue(candidate.appointmentId(),
                    candidate.startTime(), candidate.patientName());
            if (send(candidate, message, key, now)) overdue++; else skipped++;
        }
        if (reminders + overdue > 0) log.info("Reminders sent: {} upcoming, {} overdue follow-up", reminders, overdue);
        return new RunResult(reminders, overdue, skipped);
    }

    /** One failing candidate never stops the batch; it is retried by the next run. */
    private boolean send(Candidate candidate, NotificationMessage message, String key, Instant now) {
        try {
            UUID recipient = recipients.patientAccount(candidate.patientProfileId()).orElse(null);
            if (recipient == null) return false;
            return notifications.insertIfAbsent(UUID.randomUUID(), recipient, message.type(), message.title(),
                    message.content(), message.relatedKind().name(), message.relatedId(), now, key) == 1;
        } catch (RuntimeException failure) {
            log.error("Reminder {} could not be stored", key, failure);
            return false;
        }
    }
}
