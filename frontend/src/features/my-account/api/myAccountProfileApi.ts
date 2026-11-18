import { updateCurrentPatientDisplayName } from "@/lib/auth/currentPatientAccountApi";
import { getMyAccount } from "./myAccountApi";
import type { MyAccount } from "../types/myAccount";

export async function updateMyDisplayName(
  displayName: string,
): Promise<MyAccount> {
  await updateCurrentPatientDisplayName(displayName);
  return getMyAccount();
}
