import { apiRequest } from "@/lib/api/client";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import {
  getLinkedProfileRecords,
  isLinkedProfilesMockFailing,
} from "./linkedProfilesMockStore";
import type { LinkedProfile, LinkedProfileList } from "./types";

// Legacy fixtures remain available for existing tests outside KAN-48.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Bản thân đứng đầu, sau đó theo tên (so sánh theo tiếng Việt). */
function selfFirstThenByName(a: LinkedProfile, b: LinkedProfile): number {
  const selfOrder =
    Number(b.relationship === "SELF") - Number(a.relationship === "SELF");
  return selfOrder || a.fullName.localeCompare(b.fullName, "vi");
}

type ProfileItem = {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string;
  gender: LinkedProfile["sex"];
  relationship: CareRelationship;
  linkStatus: string;
};

export async function listConfirmedProfiles(
  signal?: AbortSignal,
): Promise<LinkedProfileList> {
  const { items } = await apiRequest<{ items: ProfileItem[] }>(
    "/api/v1/me/patient-profiles",
    { signal },
  );
  return {
    items: items
      .filter((item) => item.linkStatus === "CONFIRMED")
      .map((item) => ({
        id: item.patientProfileId,
        patientCode: "",
        fullName: item.fullName,
        dateOfBirth: item.dateOfBirth,
        sex: item.gender,
        relationship: item.relationship,
      }))
      .sort(selfFirstThenByName),
  };
}

export async function listLinkedProfiles(
  signal?: AbortSignal,
): Promise<LinkedProfileList> {
  if (process.env.NODE_ENV !== "test") return listConfirmedProfiles(signal);
  await mockDelay(LATENCY_MS, signal);
  if (isLinkedProfilesMockFailing()) mockFail(500, "MSG-04");
  return { items: getLinkedProfileRecords().sort(selfFirstThenByName) };
}
