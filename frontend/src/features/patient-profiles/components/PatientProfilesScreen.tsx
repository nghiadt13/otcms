"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  dependentErrorMessage,
  listManagedProfiles,
  type ManagedProfile,
} from "../api/dependentApi";
import { MedicalHistoryProfiles } from "./MedicalHistoryProfiles";
import { PortalLoadError } from "./PortalLoadError";

const statusLabels = {
  CONFIRMED: "Đã liên kết",
  PENDING_OTP: "Chờ OTP",
  PENDING_COUNTER: "Chờ xác nhận tại quầy",
};

export function PatientProfilesScreen() {
  const [profiles, setProfiles] = useState<ManagedProfile[] | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listManagedProfiles(controller.signal).then(
      ({ items }) => {
        if (!controller.signal.aborted) setProfiles(items);
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setError(dependentErrorMessage(cause));
      },
    );
    return () => controller.abort();
  }, [attempt]);

  return (
    <>
      <PortalPageHeader
        title="Hồ sơ bệnh nhân"
        description="Xem hồ sơ của bạn và theo dõi yêu cầu liên kết người thân."
        actions={
          <Link
            className={publicButtonClass("primary")}
            href={PATIENT_ROUTES.newProfile}
          >
            Thêm người thân
          </Link>
        }
      />
      <div className="space-y-6">
        <MedicalHistoryProfiles />
        {error ? (
          <PortalLoadError
            title="Không tải được hồ sơ."
            message={error}
            onRetry={() => {
              setError("");
              setAttempt((value) => value + 1);
            }}
          />
        ) : profiles === null ? (
          <p role="status">Đang tải hồ sơ…</p>
        ) : profiles.length === 0 ? (
          <p>Chưa có hồ sơ bệnh nhân.</p>
        ) : (
          <ul className="divide-y rounded-lg border bg-card">
            {profiles.map((profile) => (
              <li
                key={profile.linkId ?? profile.patientProfileId}
                className="flex flex-wrap items-center justify-between gap-3 p-4"
              >
                <div>
                  <p className="font-medium">{profile.fullName}</p>
                  <p className="text-sm text-muted-foreground">
                    {CARE_RELATIONSHIP_LABELS[profile.relationship]}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-muted px-3 py-1 text-sm">
                    {profile.relationship === "SELF"
                      ? "Hồ sơ của bạn"
                      : statusLabels[profile.linkStatus]}
                  </span>
                  {profile.linkStatus === "CONFIRMED" && (
                    <Link
                      className="text-sm underline"
                      href={PATIENT_ROUTES.profileDetail(
                        profile.patientProfileId,
                      )}
                    >
                      Xem hồ sơ
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
