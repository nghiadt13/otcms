"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { isApiError } from "@/lib/api/apiError";
import { todayIsoDate } from "@/lib/format/formatDate";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { checkInPatient } from "../api/checkInApi";
import { useCheckInContext } from "../hooks/useCheckInContext";
import { useExamFeeDraft } from "../hooks/useExamFeeDraft";
import { usePriorityGroups } from "../hooks/usePriorityGroups";
import {
  buildCheckInRequest,
  defaultOptionKey,
  findOption,
} from "../schema/checkInDraft";
import { checkInReadiness } from "../schema/checkInReadiness";
import { roomForQueueMessage } from "../schema/roomForQueueMessage";
import type { CheckInResult, CheckInTarget } from "../types/checkIn";
import { CheckInOptionPicker } from "./CheckInOptionPicker";
import { CheckInPanelError, CheckInPanelSkeleton } from "./CheckInPanelStates";
import { CheckInReadiness } from "./CheckInReadiness";
import { ExamFeeBlock } from "./ExamFeeBlock";
import { IneligibleNotices } from "./IneligibleNotices";
import { PatientIdentityHeader } from "./PatientIdentityHeader";
import { PriorityGroupSelect } from "./PriorityGroupSelect";
import { QueueTicket } from "./QueueTicket";
import { WalkInServicePicker } from "./WalkInServicePicker";

interface CheckInPanelProps {
  target: CheckInTarget;
  /** Đích không còn truy cập được (404/403): màn bỏ lựa chọn để quay về danh sách. */
  onInvalidTarget: () => void;
  onCheckedIn: () => void;
  onNext: () => void;
}

// Panel tiếp nhận của một lịch hẹn hoặc bệnh nhân: tải bối cảnh, giữ lựa chọn của lễ tân, thu
// phí (useExamFeeDraft) rồi tiếp nhận. Đổi đích thì màn remount (key ở Workspace), nên lựa chọn
// cũ được bỏ hết.
export function CheckInPanel({ target, onInvalidTarget, onCheckedIn, onNext }: CheckInPanelProps) {
  const { data, error, reload } = useCheckInContext(target);
  const priority = usePriorityGroups();

  const [optionChoice, setOptionChoice] = useState<string | null>(null);
  const [serviceId, setServiceId] = useState<string | null>(null);
  const [priorityCode, setPriorityCode] = useState<string | null>(null);
  const [present, setPresent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [ticket, setTicket] = useState<{ patientName: string; result: CheckInResult } | null>(null);

  useEffect(() => {
    if (isApiError(error) && (error.status === 403 || error.status === 404)) {
      notifyError(error);
      onInvalidTarget();
    }
  }, [error, onInvalidTarget]);

  const activeKey = optionChoice ?? (data ? defaultOptionKey(data.options, target) : null);
  const option = data ? findOption(data.options, activeKey) : null;
  const fee = useExamFeeDraft({
    patientId: data?.patient.id ?? null,
    option,
    serviceId,
    onStale: reload,
  });
  const missing = checkInReadiness({
    optionKind: option?.kind ?? null,
    serviceChosen: serviceId !== null,
    feeStatus: fee.view?.kind === "FEE" ? fee.view.status : null,
    present,
  });

  function chooseOption(key: string) {
    if (fee.locked) return;
    setOptionChoice(key);
    fee.resetPayment();
  }

  function chooseService(id: string) {
    if (fee.locked) return;
    setServiceId(id);
    fee.resetPayment();
  }

  async function submit() {
    if (!data || !option || missing.length > 0) return;
    setSubmitting(true);
    try {
      const result = await checkInPatient(
        buildCheckInRequest({
          patientId: data.patient.id,
          option,
          serviceId,
          priorityGroupCode: priorityCode,
          payment: fee.payment,
        }),
      );
      setTicket({ patientName: data.patient.fullName, result });
      notifySuccess("MSG-94", {
        number: result.queueNumber,
        room: roomForQueueMessage(result.roomName),
      });
      onCheckedIn();
    } catch (failure) {
      notifyError(failure);
      // Lịch đã bị người khác tiếp nhận hoặc phí đã trả nơi khác: tải lại để thấy trạng thái thật.
      if (isApiError(failure) && (failure.code === "MSG-37" || failure.code === "MSG-49")) reload();
    } finally {
      setSubmitting(false);
    }
  }

  if (ticket) {
    return <QueueTicket patientName={ticket.patientName} result={ticket.result} onNext={onNext} />;
  }
  if (!data) {
    return error ? <CheckInPanelError onRetry={reload} /> : <CheckInPanelSkeleton />;
  }

  return (
    <div className="space-y-5" inert={submitting}>
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          Không tải lại được thông tin tiếp nhận, dữ liệu dưới đây có thể đã cũ.{" "}
          <Button variant="link" className="h-auto p-0" onClick={reload}>
            Thử lại
          </Button>
        </p>
      ) : null}
      <PatientIdentityHeader patient={data.patient} />
      <IneligibleNotices items={data.ineligible} notice={data.notice} today={todayIsoDate()} />
      {data.options.length > 0 ? (
        <section className="space-y-5 border-t pt-5">
          <CheckInOptionPicker
            options={data.options}
            selectedKey={activeKey}
            onChange={chooseOption}
            disabled={fee.locked || submitting}
          />
          {option?.kind === "WALK_IN" ? (
            <WalkInServicePicker
              services={option.services}
              selectedId={serviceId}
              onChange={chooseService}
              disabled={fee.locked || submitting}
            />
          ) : null}
          <PriorityGroupSelect
            groups={priority.groups}
            loading={priority.loading}
            error={priority.error}
            value={priorityCode}
            onChange={setPriorityCode}
            onRetry={priority.reload}
            disabled={submitting}
          />
        </section>
      ) : null}
      {fee.view ? (
        <ExamFeeBlock
          view={fee.view}
          payment={fee.payment}
          method={fee.method}
          collecting={fee.collecting}
          onMethodChange={fee.chooseMethod}
          onCollectCash={() => void fee.collectCash()}
          onCreateQr={() => void fee.createQr()}
          onSwitchToCash={fee.switchToCash}
          onQrSuccess={fee.handleQrSuccess}
          onQrFailed={fee.handleQrFailed}
        />
      ) : null}
      {option ? (
        <CheckInReadiness
          present={present}
          onPresentChange={setPresent}
          missing={missing}
          submitting={submitting}
          onSubmit={() => void submit()}
        />
      ) : null}
    </div>
  );
}
