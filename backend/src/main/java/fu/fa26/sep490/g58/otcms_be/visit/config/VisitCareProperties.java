package fu.fa26.sep490.g58.otcms_be.visit.config;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.OptionalInt;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otcms.visit")
public class VisitCareProperties {
    private Map<String, Integer> careStepDurationMinutes = Map.of();
    private Map<String, String> careStepRoomCapability = Map.of();
    private Map<String, Integer> roomCleanupBufferMinutes = Map.of();

    public Map<String, Integer> getCareStepDurationMinutes() {
        return careStepDurationMinutes;
    }

    public void setCareStepDurationMinutes(Map<String, Integer> careStepDurationMinutes) {
        if (careStepDurationMinutes == null) {
            this.careStepDurationMinutes = Map.of();
            return;
        }
        var copy = new java.util.LinkedHashMap<String, Integer>();
        careStepDurationMinutes.forEach((code, minutes) -> {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}") || minutes == null || minutes < 1) {
                throw new IllegalArgumentException("otcms.visit.care-step-duration-minutes requires uppercase step codes and positive durations");
            }
            copy.put(code, minutes);
        });
        this.careStepDurationMinutes = Map.copyOf(copy);
    }

    public OptionalInt durationMinutes(String stepCode) {
        Integer minutes = careStepDurationMinutes.get(stepCode);
        return minutes == null ? OptionalInt.empty() : OptionalInt.of(minutes);
    }

    public Map<String, String> getCareStepRoomCapability() { return careStepRoomCapability; }

    public void setCareStepRoomCapability(Map<String, String> mappings) {
        this.careStepRoomCapability = validatedMap(mappings, true,
                "otcms.visit.care-step-room-capability requires uppercase step and capability codes or NONE");
    }

    public Map<String, Integer> getRoomCleanupBufferMinutes() { return roomCleanupBufferMinutes; }

    public void setRoomCleanupBufferMinutes(Map<String, Integer> buffers) {
        if (buffers == null) {
            this.roomCleanupBufferMinutes = Map.of();
            return;
        }
        var copy = new LinkedHashMap<String, Integer>();
        buffers.forEach((capability, minutes) -> {
            if (capability == null || !capability.matches("[A-Z][A-Z0-9_]{0,63}")
                    || "NONE".equals(capability) || minutes == null || minutes < 0) {
                throw new IllegalArgumentException("otcms.visit.room-cleanup-buffer-minutes requires capabilities and nonnegative minutes");
            }
            copy.put(capability, minutes);
        });
        this.roomCleanupBufferMinutes = Map.copyOf(copy);
    }

    public java.util.Optional<String> roomCapability(String stepCode) {
        return java.util.Optional.ofNullable(careStepRoomCapability.get(stepCode));
    }

    public OptionalInt cleanupBufferMinutes(String capability) {
        Integer minutes = roomCleanupBufferMinutes.get(capability);
        return minutes == null ? OptionalInt.empty() : OptionalInt.of(minutes);
    }

    private static Map<String, String> validatedMap(Map<String, String> mappings, boolean allowNone, String message) {
        if (mappings == null) return Map.of();
        var copy = new LinkedHashMap<String, String>();
        mappings.forEach((step, capability) -> {
            if (step == null || !step.matches("[A-Z][A-Z0-9_]{0,63}") || capability == null
                    || !(capability.matches("[A-Z][A-Z0-9_]{0,63}")
                    || (allowNone && "NONE".equals(capability)))) {
                throw new IllegalArgumentException(message);
            }
            copy.put(step, capability);
        });
        return Map.copyOf(copy);
    }
}
