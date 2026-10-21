import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";

/**
 * Loại phòng gợi ý theo vai trò (chỉ gợi ý, Admin đổi được).
 * Lễ tân chưa có loại phòng phù hợp (câu hỏi mở 1) nên không gợi ý.
 */
export function suggestRoomCategory(role: StaffRole): ServiceCategory | null {
  switch (role) {
    case "ORTHOPEDIC_DOCTOR":
      return "EXAMINATION";
    case "IMAGING_TECHNICIAN":
      return "IMAGING";
    case "LAB_TECHNICIAN":
      return "LABORATORY";
    default:
      return null;
  }
}
