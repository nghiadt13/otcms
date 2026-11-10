package fu.fa26.sep490.g58.otcms_be.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;
import java.util.Objects;

/** Typed booking policy supplied by the deployment configuration. */
@ConfigurationProperties(prefix = "appointment.booking")
public class AppointmentBookingProperties {
    private final int maxHorizonDays;
    private final int patientChangeDeadlineMinutes;
    private final int checkInEarlyMinutes;
    private final int checkInLateGraceMinutes;
    private final int slotIntervalMinutes;
    private final ZoneId clinicTimeZone;
    private final PublicRateLimit publicRateLimit;
    private final int paymentHoldMinutes;
    private final long paymentHoldSweepMs;
    private final boolean paymentHoldSweepEnabled;
    @ConstructorBinding
    public AppointmentBookingProperties(int maxHorizonDays, int patientChangeDeadlineMinutes, int checkInEarlyMinutes,
            int checkInLateGraceMinutes, int slotIntervalMinutes, ZoneId clinicTimeZone, PublicRateLimit publicRateLimit,
            @DefaultValue("15") int paymentHoldMinutes, @DefaultValue("30000") long paymentHoldSweepMs,
            @DefaultValue("true") boolean paymentHoldSweepEnabled) {
        if (maxHorizonDays < 1) {
            throw new IllegalArgumentException("appointment.booking.max-horizon-days must be positive");
        }
        requireNonNegative(patientChangeDeadlineMinutes, "patient-change-deadline-minutes");
        requireNonNegative(checkInEarlyMinutes, "check-in-early-minutes");
        requireNonNegative(checkInLateGraceMinutes, "check-in-late-grace-minutes");
        if (slotIntervalMinutes < 1 || 1440 % slotIntervalMinutes != 0) {
            throw new IllegalArgumentException(
                    "appointment.booking.slot-interval-minutes must be positive and divide 1440");
        }
        if (clinicTimeZone == null) {
            throw new IllegalArgumentException("appointment.booking.clinic-time-zone is required");
        }
        if (publicRateLimit == null) {
            throw new IllegalArgumentException("appointment.booking.public-rate-limit is required");
        }
        if (paymentHoldMinutes < 1) {
            throw new IllegalArgumentException("appointment.booking.payment-hold-minutes must be positive");
        }
        if (paymentHoldSweepMs < 1) {
            throw new IllegalArgumentException("appointment.booking.payment-hold-sweep-ms must be positive");
        }
        this.paymentHoldMinutes = paymentHoldMinutes;
        this.paymentHoldSweepMs = paymentHoldSweepMs;
        this.paymentHoldSweepEnabled = paymentHoldSweepEnabled;
        this.maxHorizonDays = maxHorizonDays;
        this.patientChangeDeadlineMinutes = patientChangeDeadlineMinutes;
        this.checkInEarlyMinutes = checkInEarlyMinutes;
        this.checkInLateGraceMinutes = checkInLateGraceMinutes;
        this.slotIntervalMinutes = slotIntervalMinutes;
        this.clinicTimeZone = clinicTimeZone;
        this.publicRateLimit = publicRateLimit;
    }
    public int getMaxHorizonDays() { return maxHorizonDays; }
    public int maxHorizonDays() { return maxHorizonDays; }
    public int getPatientChangeDeadlineMinutes() { return patientChangeDeadlineMinutes; }
    public int patientChangeDeadlineMinutes() { return patientChangeDeadlineMinutes; }
    public int getCheckInEarlyMinutes() { return checkInEarlyMinutes; }
    public int checkInEarlyMinutes() { return checkInEarlyMinutes; }
    public int getCheckInLateGraceMinutes() { return checkInLateGraceMinutes; }
    public int checkInLateGraceMinutes() { return checkInLateGraceMinutes; }
    public int getSlotIntervalMinutes() { return slotIntervalMinutes; }
    public int slotIntervalMinutes() { return slotIntervalMinutes; }
    public ZoneId getClinicTimeZone() { return clinicTimeZone; }
    public ZoneId clinicTimeZone() { return clinicTimeZone; }
    public PublicRateLimit getPublicRateLimit() { return publicRateLimit; }
    public PublicRateLimit publicRateLimit() { return publicRateLimit; }
    /** How long an online booking with a fee holds the doctor slot while the patient pays. */
    public int getPaymentHoldMinutes() { return paymentHoldMinutes; }
    public int paymentHoldMinutes() { return paymentHoldMinutes; }
    public long getPaymentHoldSweepMs() { return paymentHoldSweepMs; }
    public long paymentHoldSweepMs() { return paymentHoldSweepMs; }
    public boolean isPaymentHoldSweepEnabled() { return paymentHoldSweepEnabled; }
    public boolean paymentHoldSweepEnabled() { return paymentHoldSweepEnabled; }
    private static void requireNonNegative(int value, String property) {
        if (value < 0) {
            throw new IllegalArgumentException("appointment.booking." + property + " must be non-negative");
        }
    }

        public static class PublicRateLimit {
    private final int requests;
    private final int windowSeconds;
    private final String trustedProxyCidrs;
    @ConstructorBinding
    public PublicRateLimit(int requests, int windowSeconds, String trustedProxyCidrs) {
        if (requests < 1) {
                throw new IllegalArgumentException("appointment.booking.public-rate-limit.requests must be positive");
            }
            if (windowSeconds < 1) {
                throw new IllegalArgumentException(
                        "appointment.booking.public-rate-limit.window-seconds must be positive");
            }
            trustedProxyCidrs = trustedProxyCidrs == null ? "" : trustedProxyCidrs.trim();
        this.requests = requests;
        this.windowSeconds = windowSeconds;
        this.trustedProxyCidrs = trustedProxyCidrs;
    }
    public int getRequests() { return requests; }
    public int requests() { return requests; }
    public int getWindowSeconds() { return windowSeconds; }
    public int windowSeconds() { return windowSeconds; }
    public String getTrustedProxyCidrs() { return trustedProxyCidrs; }
    public String trustedProxyCidrs() { return trustedProxyCidrs; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof PublicRateLimit value
                && requests == value.requests && windowSeconds == value.windowSeconds
                && Objects.equals(trustedProxyCidrs, value.trustedProxyCidrs);
    }
    @Override public int hashCode() { return Objects.hash(requests, windowSeconds, trustedProxyCidrs); }
    public PublicRateLimit(int requests, int windowSeconds) {
            this(requests, windowSeconds, "");
        }
}
}
