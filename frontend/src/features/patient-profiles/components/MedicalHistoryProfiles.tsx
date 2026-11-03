"use client";

import Link from "next/link";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { canOpenMedicalHistory } from "../api/medicalHistoryProfilesApi";
import { medicalHistoryErrorMessage } from "../api/medicalHistoryErrors";
import { useMedicalHistoryProfiles } from "../hooks/useMedicalHistoryProfiles";
import { PortalLoadError } from "./PortalLoadError";

/** Lối vào UC12 dùng ID thật, độc lập với danh sách hồ sơ hành chính còn mock. */
export function MedicalHistoryProfiles() {
  const { profiles, error, reload } = useMedicalHistoryProfiles();
  const accessible = profiles?.filter(canOpenMedicalHistory) ?? [];
  return (
    <section aria-label="Tiền sử bệnh" className="space-y-4">
      <h2 className="text-lg font-semibold">Tiền sử bệnh</h2>
      {error ? (
        <PortalLoadError
          title="Không tải được hồ sơ xem tiền sử."
          message={medicalHistoryErrorMessage(error)}
          onRetry={reload}
        />
      ) : !profiles ? (
        <div aria-busy="true" role="status">
          <span className="sr-only">Đang tải hồ sơ xem tiền sử</span>
          <Skeleton className="h-16 w-full" />
        </div>
      ) : accessible.length === 0 ? (
        <p className="text-base text-muted-foreground">
          Chưa có hồ sơ đã xác nhận để xem tiền sử bệnh.
        </p>
      ) : (
        <ul className="divide-y overflow-hidden rounded-lg border bg-card">
          {accessible.map((profile) => (
            <li key={profile.patientProfileId}>
              <Link
                className="flex min-h-14 flex-wrap items-center justify-between gap-2 px-4 py-3 text-base text-primary hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
                href={PATIENT_ROUTES.profileHistory(profile.patientProfileId)}
              >
                <span>
                  {profile.fullName} ·{" "}
                  {CARE_RELATIONSHIP_LABELS[profile.relationship]}
                </span>
                <span>Xem tiền sử bệnh</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
