import type { MyAccountResponse } from "../types/myAccount";

export const ACCOUNT_ID = "6f1c2a9e-3b7d-4e8a-9c21-5d0a7b3e4f10";
export const OTHER_ACCOUNT_ID = "6f1c2a9e-3b7d-4e8a-9c21-5d0a7b3e4f11";

/** Chỉ là response fixture tại ranh giới HTTP; không thay thế nghiệp vụ của feature. */
export function accountResponse(
  overrides: Partial<MyAccountResponse> = {},
): MyAccountResponse {
  return {
    id: ACCOUNT_ID,
    accountType: "ADMIN",
    kind: "STAFF",
    role: "ADMIN",
    username: "admin.account",
    email: "",
    displayName: "Tên từ backend",
    avatarUrl: null,
    status: "ACTIVE",
    mustChangePassword: false,
    allowedActions: ["updateDisplayName", "updateAvatar", "changePassword"],
    ...overrides,
  };
}
export function patientResponse(
  overrides: Partial<MyAccountResponse> = {},
): MyAccountResponse {
  return accountResponse({
    kind: "PATIENT",
    accountType: "PATIENT",
    role: "PATIENT",
    username: "",
    email: "patient@example.test",
    ...overrides,
  });
}
