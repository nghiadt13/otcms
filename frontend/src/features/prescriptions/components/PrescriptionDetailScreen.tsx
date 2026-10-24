"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound, useRouter } from "next/navigation";
import { useEffect } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { usePrescriptionDocument } from "../hooks/usePrescriptionDocument";
import { PrescriptionDocumentView } from "./PrescriptionDocumentView";

/**
 * Màn một đơn thuốc. 403/404 đi tới trang "không tìm thấy"; 409 (đơn chưa phát hành)
 * báo bằng toast rồi về danh sách; lỗi khác hiện thông báo kèm nút thử lại.
 */
export function PrescriptionDetailScreen({ prescriptionId }: { prescriptionId: string }) {
  const router = useRouter();
  const { prescription, loading, error, reload } = usePrescriptionDocument(prescriptionId);
  const code = isApiError(error) ? error.code : undefined;
  const notAccessible = code === "MSG-40" || code === "MSG-41";
  const notIssued = code === "MSG-42";

  useEffect(() => {
    if (!notIssued) return;
    notifyError(error);
    router.replace(PATIENT_ROUTES.prescriptions);
  }, [notIssued, error, router]);

  if (notAccessible) notFound();
  if (notIssued) return null;
  if (error) return <DetailError message={messageFromError(error)} onRetry={reload} />;
  if (loading || !prescription) return <DetailSkeleton />;

  return (
    <div className="space-y-6">
      <Link
        href={PATIENT_ROUTES.prescriptions}
        className="inline-flex min-h-11 items-center gap-1 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ChevronLeft aria-hidden />
        Đơn thuốc
      </Link>
      <PortalPageHeader
        title={`Đơn thuốc ngày ${formatDate(prescription.issuedAt)}`}
        description={`${prescription.patient.fullName} (${CARE_RELATIONSHIP_LABELS[prescription.patient.relationship]})`}
      />
      <PrescriptionDocumentView document={prescription} />
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div aria-hidden className="space-y-6">
      <Skeleton className="h-10 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

function DetailError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="space-y-4 rounded-lg border bg-card px-6 py-8 text-center">
      <p className="text-base text-destructive">{message}</p>
      <Button variant="outline" className="h-12 px-5 text-base" onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  );
}
