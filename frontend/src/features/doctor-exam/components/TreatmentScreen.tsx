"use client";

import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import {
  useAdvice,
  useDoctorExamApi,
  useExamination,
  usePrescription,
} from "../hooks/useDoctorData";
import { AdviceForm } from "./AdviceForm";
import { FollowUpDialog } from "./FollowUpDialog";
import { PrescriptionForm } from "./PrescriptionForm";
import { isVisitClosed } from "./doctorStatus";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";

// Tab Điều trị: lời dặn (UC42), đơn thuốc (UC43) và hẹn tái khám (UC21).

export function TreatmentScreen() {
  const { visitId, context } = useVisitWorkspace();
  const examination = useExamination(visitId);
  const advice = useAdvice(visitId);
  const prescription = usePrescription(visitId);
  const api = useDoctorExamApi();
  const closed = isVisitClosed(context?.status ?? null);
  const loading = examination.loading || advice.loading || prescription.loading;
  const error = examination.error ?? advice.error ?? prescription.error;
  const noExamination = examination.data?.status === null;

  return (
    <>
      <PageHeader
        title="Điều trị và đơn thuốc"
        description="Lời dặn sau khám, kê đơn điện tử và hẹn tái khám cho bệnh nhân."
        actions={
          <FollowUpDialog disabled={closed || !context?.doctorProfileId} />
        }
      />
      {loading || error ? (
        <DoctorLoadState
          loading={loading}
          error={error}
          retry={() => {
            examination.refresh();
            advice.refresh();
            prescription.refresh();
          }}
        />
      ) : (
        <div className="space-y-6">
          <AdviceForm
            key={`advice-${advice.data?.version ?? "new"}`}
            visitId={visitId}
            initial={advice.data ?? null}
            disabled={closed || noExamination}
            disabledReason={
              noExamination
                ? "Cần lưu ghi nhận khám (tab Khám bệnh) trước khi ghi lời dặn."
                : undefined
            }
            onSaved={advice.refresh}
          />
          <PrescriptionForm
            key={`rx-${prescription.data?.issuedAt ?? prescription.data?.status ?? "new"}`}
            visitId={visitId}
            initial={prescription.data ?? null}
            available={api.capabilities.prescriptions}
            locked={closed}
            onSaved={prescription.refresh}
          />
        </div>
      )}
    </>
  );
}
