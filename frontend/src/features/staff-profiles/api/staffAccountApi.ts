import { apiRequest } from "@/lib/api/client";
import type { StaffRole } from "@/lib/domain/clinicRoles";

export type IssuedStaffAccount = {
  accountId: string;
  username: string;
  role: StaffRole;
  temporaryPassword: string;
};

export function createStaffAccount(input: {
  username: string;
  initialPassword: string;
  role: StaffRole;
}) {
  return apiRequest<{ accountId: string; username: string; role: StaffRole }>("/api/v1/admin/staff-accounts", {
    method: "POST",
    body: JSON.stringify({
      ...input,
      role: input.role === "ORTHOPEDIC_DOCTOR" ? "DOCTOR" : input.role,
    }),
  }).then((result): IssuedStaffAccount => ({
    ...result,
    role: input.role,
    temporaryPassword: input.initialPassword,
  }));
}
