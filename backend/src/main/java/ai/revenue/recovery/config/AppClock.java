package ai.revenue.recovery.config;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Centralized clock utility for all new time-critical code.
 * Uses an explicit ZoneId so the sweeper's 15-minute staleness check
 * and the guardrail's 24-hour expiry are self-protecting even if
 * the JVM-level timezone pin is removed.
 */
public final class AppClock {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AppClock() {
        // Utility class — no instantiation
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static ZoneId zone() {
        return ZONE;
    }
}
