import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { SEX_LABELS } from "./profileOptions";

// Danh sách hồ sơ đã gắn: một khung có vạch chia, cả dòng là link. Mỗi dòng ghi tên, quan hệ
// (của ai) và mã bệnh nhân.
export function PatientProfileList({
  profiles,
}: {
  profiles: LinkedProfile[];
}) {
  return (
    <ul className="divide-y overflow-hidden rounded-lg border bg-card">
      {profiles.map((profile) => (
        <li key={profile.id}>
          <Link
            className="flex min-h-16 items-center gap-3 px-4 py-3 transition-colors hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none motion-reduce:transition-none"
            href={PATIENT_ROUTES.profileDetail(profile.id)}
          >
            <span className="min-w-0 flex-1">
              <span className="flex flex-wrap items-center gap-2 text-base font-medium">
                {profile.fullName}
                <Badge variant="secondary">
                  {CARE_RELATIONSHIP_LABELS[profile.relationship]}
                </Badge>
              </span>
              <span className="mt-0.5 block text-sm text-muted-foreground tabular-nums">
                {profile.patientCode} · {formatDate(profile.dateOfBirth)} ·{" "}
                {SEX_LABELS[profile.sex]}
              </span>
            </span>
            <ChevronRight
              aria-hidden
              className="size-5 shrink-0 text-muted-foreground"
            />
          </Link>
        </li>
      ))}
    </ul>
  );
}
