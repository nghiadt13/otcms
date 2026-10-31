import { apiRequest } from "@/lib/api/client";
import type { StaffRole } from "@/lib/domain/clinicRoles";

export type IssuedStaffAccount = {
  accountId: string;
  email: string;
  fullName: string;
  role: StaffRole;
  temporaryPassword: string;
};

export function createStaffAccount(input: {
  email: string;
  fullName: string;
  role: StaffRole;
}) {
  return apiRequest<IssuedStaffAccount>("/api/v1/admin/staff-accounts", {
    method: "POST",
    body: JSON.stringify({
      ...input,
      role: input.role === "ORTHOPEDIC_DOCTOR" ? "DOCTOR" : input.role,
    }),
  });
}
