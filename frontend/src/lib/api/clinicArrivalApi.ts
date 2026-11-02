import { apiRequest } from "@/lib/api/client";

export type ReceptionArrival = {
  appointmentId: string;
  patientProfileId: string;
  source: string;
  arrivedAt: string;
  state: string;
  recordedAt: string;
};

export type PatientQrCheckIn = {
  appointmentId: string;
  patientProfileId: string;
  source: string;
  state: string;
  arrivedAt: string;
  recordedAt: string;
  appointmentStatus: string;
  visitId: string | null;
  checkedInAt: string | null;
  checkedIn: boolean;
};

export type ClinicArrivalProof = {
  proof: string;
  issuedAt: string;
  expiresAt: string;
  refreshAfterSeconds: number;
};

export function getCurrentClinicArrivalProof(signal?: AbortSignal) {
  return apiRequest<ClinicArrivalProof>("/api/v1/arrival-proofs/current", {
    signal,
  });
}

export function recordPatientQrArrival(appointmentId: string, proof: string) {
  return apiRequest<PatientQrCheckIn>(
    `/api/v1/appointments/${appointmentId}/arrivals`,
    { method: "POST", body: JSON.stringify({ proof }) },
  );
}
