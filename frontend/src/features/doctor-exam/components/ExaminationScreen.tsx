"use client";

import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { useExamination } from "../hooks/useDoctorData";
import { ExaminationForm } from "./ExaminationForm";
import { ParaclinicalResultsSummary } from "./ParaclinicalResultsSummary";
import { isVisitClosed } from "./doctorStatus";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";

export function ExaminationScreen() {
  const { visitId, context } = useVisitWorkspace();
  const { data, error, loading, refresh } = useExamination(visitId);

  return (
    <>
      <PageHeader
        title="Khám bệnh"
        description="Ghi nhận lý do khám, khám lâm sàng và chẩn đoán ICD-10; xem kèm kết quả cận lâm sàng đã có."
      />
      {loading || error || !data ? (
        <DoctorLoadState loading={loading} error={error} retry={refresh} />
      ) : (
        <ExaminationForm
          key={`${data.status}-${data.version}`}
          visitId={visitId}
          initial={data}
          locked={isVisitClosed(context?.status ?? null)}
          aside={<ParaclinicalResultsSummary visitId={visitId} />}
          onSaved={refresh}
        />
      )}
    </>
  );
}
