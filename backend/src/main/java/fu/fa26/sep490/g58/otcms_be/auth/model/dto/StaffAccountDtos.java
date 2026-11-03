package fu.fa26.sep490.g58.otcms_be.auth.model.dto;

import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService.StaffProfileSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StaffAccountDtos {
    private StaffAccountDtos() { }
    public record CreateRequest(@NotBlank String role, @NotBlank @Size(max = 64) String username,
            @NotBlank String initialPassword) {
        @Override public String toString() { return "StaffAccountCreateRequest[REDACTED]"; }
    }
    public record Created(UUID accountId, String username, String role, String status, boolean mustChangePassword) { }
    public record Item(UUID accountId, String username, String role, String status, boolean mustChangePassword,
            Instant createdAt, StaffProfileSummary staffProfile) { }
    public record Page(List<Item> items, int page, int size, long totalItems, int totalPages) { }
    public record Reissued(UUID accountId, String temporaryPassword, boolean mustChangePassword) {
        @Override public String toString() { return "StaffAccountReissued[REDACTED]"; }
    }
}
