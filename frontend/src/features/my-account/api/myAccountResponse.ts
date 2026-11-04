import { ApiError } from "@/lib/api/apiError";
import type {
  MyAccount,
  MyAccountAction,
  MyAccountResponse,
} from "../types/myAccount";

const ROLES = {
  PATIENT: "PATIENT",
  ADMIN: "ADMIN",
  OWNER: "CLINIC_OWNER",
  DOCTOR: "ORTHOPEDIC_DOCTOR",
  RECEPTIONIST: "RECEPTIONIST",
  LAB_TECHNICIAN: "LAB_TECHNICIAN",
  IMAGING_TECHNICIAN: "IMAGING_TECHNICIAN",
} as const;
const ACTIONS: readonly MyAccountAction[] = [
  "updateAvatar",
  "removeAvatar",
  "changePassword",
  "updateDisplayName",
  "changeEmail",
];

function invalidResponse(): never {
  throw new ApiError(502, {
    code: "MSG-04",
    errorCode: "INVALID_ACCOUNT_RESPONSE",
  });
}

/** Projection rõ trường; không đưa trường lạ, default giả hoặc quyền mock vào UI. */
export function mapMyAccountResponse(input: unknown): MyAccount {
  if (!input || typeof input !== "object" || Array.isArray(input))
    return invalidResponse();
  const value = input as Partial<MyAccountResponse>;
  if (
    typeof value.id !== "string" ||
    !value.id ||
    typeof value.accountType !== "string" ||
    !Object.hasOwn(ROLES, value.accountType) ||
    typeof value.displayName !== "string" ||
    typeof value.email !== "string" ||
    typeof value.username !== "string" ||
    typeof value.mustChangePassword !== "boolean" ||
    (value.status !== "ACTIVE" && value.status !== "INACTIVE") ||
    (value.avatarUrl !== null && typeof value.avatarUrl !== "string") ||
    !Array.isArray(value.allowedActions) ||
    !value.allowedActions.every((action) => typeof action === "string")
  )
    return invalidResponse();
  const accountType = value.accountType as keyof typeof ROLES;
  if (
    value.role !== ROLES[accountType] ||
    value.kind !== (accountType === "PATIENT" ? "PATIENT" : "STAFF")
  )
    return invalidResponse();

  const base = {
    id: value.id,
    displayName: value.displayName,
    avatarUrl: value.avatarUrl,
    status: value.status,
    mustChangePassword: value.mustChangePassword,
    allowedActions: value.allowedActions.filter(
      (action): action is MyAccountAction =>
        ACTIONS.includes(action as MyAccountAction),
    ),
  };
  if (accountType === "PATIENT")
    return { ...base, kind: "PATIENT", email: value.email };
  const role = ROLES[accountType];

  return { ...base, kind: "STAFF", username: value.username, role };
}
