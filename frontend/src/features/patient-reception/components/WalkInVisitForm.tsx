"use client";

import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  EXAM_QUEUE_STATUS_LABELS,
  EXAM_QUEUE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { listOnShiftDoctors, type OnShiftDoctor } from "@/lib/api/examQueueApi";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  createReceptionWalkIn,
  listExaminationServices,
  type ExaminationService,
  type ReceptionWalkIn,
} from "../api/receptionWalkInLiveApi";
import { queuedFeedbackText } from "../schema/receptionMessages";
import { money, SelectField } from "./ReceptionUi";
import { WalkInFeeStep } from "./WalkInFeeStep";

/**
 * Tạo lượt khám trực tiếp: chọn bác sĩ đang trong ca và dịch vụ khám. Dịch vụ có giá thì lễ tân thu
 * phí tại quầy ngay; lượt được cấp số thứ tự khi thu xong (hoặc ngay nếu dịch vụ miễn phí).
 */
export function WalkInVisitForm({
  patientProfileId,
  priorAppointmentId,
}: {
  patientProfileId: string;
  priorAppointmentId?: string;
}) {
  const [doctors, setDoctors] = useState<OnShiftDoctor[]>([]);
  const [services, setServices] = useState<ExaminationService[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [feeDue, setFeeDue] = useState<{
    visitId: string;
    serviceId: string;
  } | null>(null);
  const [unpaidVisit, setUnpaidVisit] = useState(false);
  // Khoá idempotency giữ nguyên khi thử lại cùng lựa chọn sau lỗi mạng; đổi lựa chọn thì tạo khoá mới.
  const keyRef = useRef<{ fingerprint: string; key: string } | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([listOnShiftDoctors(), listExaminationServices()])
      .then(([doctorPage, serviceList]) => {
        if (!active) return;
        setDoctors(doctorPage.items);
        setServices(serviceList);
        setLoaded(true);
      })
      .catch((cause) => {
        if (!active) return;
        setLoadError(messageFromError(cause));
        setLoaded(true);
      });
    return () => {
      active = false;
    };
  }, []);

  const noDoctorOnShift = loaded && !loadError && doctors.length === 0;
  const canSubmit =
    !busy && doctorId !== "" && serviceId !== "" && !noDoctorOnShift;

  async function submit() {
    if (!canSubmit) return;
    setBusy(true);
    setError("");
    const fingerprint = [
      patientProfileId,
      priorAppointmentId ?? "",
      doctorId,
      serviceId,
    ].join("|");
    const key =
      keyRef.current?.fingerprint === fingerprint
        ? keyRef.current.key
        : crypto.randomUUID();
    keyRef.current = { fingerprint, key };
    try {
      const visit: ReceptionWalkIn = await createReceptionWalkIn({
        patientProfileId,
        priorAppointmentId,
        doctorProfileId: doctorId,
        serviceId,
        idempotencyKey: key,
      });
      keyRef.current = null;
      setDoctorId("");
      setServiceId("");
      if (visit.examStatus === "WAITING") {
        toast.success(queuedFeedbackText(visit.queueNumber, visit.roomLabel));
      } else {
        setUnpaidVisit(true);
        setFeeDue({ visitId: visit.visitId, serviceId });
      }
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm font-medium text-muted-foreground">
        Tiếp nhận trực tiếp (walk-in)
      </p>
      {loadError && (
        <p role="alert" className="text-sm text-destructive">
          {loadError}
        </p>
      )}
      {noDoctorOnShift && (
        <p role="alert" className="text-sm text-destructive">
          {formatAppMessage("MSG-04")}
        </p>
      )}
      <SelectField
        label="Bác sĩ đang trong ca"
        value={doctorId}
        onChange={setDoctorId}
        disabled={!loaded || noDoctorOnShift || busy}
        options={[
          { value: "", label: "Chọn bác sĩ" },
          ...doctors.map((doctor) => ({
            value: doctor.doctorProfileId,
            label: `${doctor.doctorName} · ${doctor.roomLabel ?? "Chưa có phòng"} · ${doctor.waitingCount} đang chờ`,
          })),
        ]}
      />
      <SelectField
        label="Dịch vụ khám"
        value={serviceId}
        onChange={setServiceId}
        disabled={!loaded || busy}
        options={[
          { value: "", label: "Chọn dịch vụ" },
          ...services.map((service) => ({
            value: service.id,
            label: `${service.name} · ${money(service.price)}`,
          })),
        ]}
      />
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <div>
        <Button
          type="button"
          variant="outline"
          disabled={!canSubmit}
          onClick={() => void submit()}
        >
          {busy ? "Đang tạo lượt khám..." : "Tạo lượt khám trực tiếp"}
        </Button>
      </div>
      {unpaidVisit && (
        <p className="text-sm">
          Lượt khám đang{" "}
          <StatusBadge tone={EXAM_QUEUE_STATUS_TONE.PENDING_PAYMENT}>
            {EXAM_QUEUE_STATUS_LABELS.PENDING_PAYMENT}
          </StatusBadge>
          . Thu phí tiếp trong mục Hàng chờ khám.
        </p>
      )}
      {feeDue && (
        <WalkInFeeStep
          visitId={feeDue.visitId}
          serviceId={feeDue.serviceId}
          onClose={() => setFeeDue(null)}
          onQueued={() => {
            setFeeDue(null);
            setUnpaidVisit(false);
          }}
        />
      )}
    </div>
  );
}
