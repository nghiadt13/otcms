"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  createReceptionAppointment,
  getStaffAppointmentAvailability,
  type ReceptionAvailabilitySlot,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import {
  listExaminationServices,
  type ExaminationService,
} from "../api/receptionWalkInLiveApi";
import { ReceptionPatientPicker } from "./ReceptionPatientPicker";
import {
  clinicLocal,
  dateText,
  FormModal,
  money,
  SelectField,
  TextField,
} from "./ReceptionUi";

const slotValue = (slot: ReceptionAvailabilitySlot) =>
  `${slot.startTime}|${slot.doctorId}`;

const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    new Date(),
  );

/**
 * Lễ tân đặt lịch khám hộ: chọn bệnh nhân, dịch vụ khám, ngày, rồi một khung giờ còn trống. Mỗi khung
 * giờ đi kèm bác sĩ có ca, nên chọn khung giờ là chọn luôn bác sĩ.
 */
export function ReceptionBookingDialog({
  onBooked,
  onClose,
}: {
  onBooked: () => void;
  onClose: () => void;
}) {
  const [patient, setPatient] = useState<ReceptionPatient | null>(null);
  const [services, setServices] = useState<ExaminationService[]>([]);
  const [serviceId, setServiceId] = useState("");
  const [date, setDate] = useState(today);
  const [slots, setSlots] = useState<ReceptionAvailabilitySlot[]>([]);
  const [slotKey, setSlotKey] = useState("");
  const [purpose, setPurpose] = useState("");
  const [loadError, setLoadError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    listExaminationServices()
      .then((list) => {
        if (active) setServices(list);
      })
      .catch((cause) => {
        if (active) setLoadError(messageFromError(cause));
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!serviceId || !date) return;
    let active = true;
    getStaffAppointmentAvailability({ serviceId, from: date, to: date })
      .then((result) => {
        if (active) setSlots(result.slots);
      })
      .catch((cause) => {
        if (active) setError(messageFromError(cause));
      });
    return () => {
      active = false;
    };
  }, [serviceId, date]);

  const selectedService = services.find((service) => service.id === serviceId);
  const selectedSlot = slots.find((slot) => slotValue(slot) === slotKey);
  const canSubmit =
    !busy &&
    patient !== null &&
    selectedService !== undefined &&
    selectedSlot !== undefined;

  // Đổi dịch vụ hoặc ngày thì khung giờ cũ không còn hợp lệ.
  function resetSlots() {
    setSlotKey("");
    setSlots([]);
  }

  async function submit() {
    if (!canSubmit || !patient || !selectedService || !selectedSlot) return;
    setBusy(true);
    setError("");
    try {
      await createReceptionAppointment({
        patientProfileId: patient.patientProfileId,
        serviceId: selectedService.id,
        doctorId: selectedSlot.doctorId,
        startTime: selectedSlot.startTime,
        purpose: purpose.trim() || selectedService.name,
      });
      toast.success(formatAppMessage("MSG-26"));
      onBooked();
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <FormModal
      title="Đặt lịch hộ bệnh nhân"
      description="Chọn bệnh nhân, dịch vụ khám và một khung giờ còn trống. Bác sĩ đi kèm khung giờ."
      onClose={onClose}
      busy={busy}
      wide
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <ReceptionPatientPicker
          onPick={(picked) => {
            setPatient(picked);
            setError("");
          }}
          onTyping={() => setPatient(null)}
        />
        {patient && (
          <p className="text-sm">
            Đã chọn: {patient.fullName} · {dateText(patient.dateOfBirth)}
          </p>
        )}
        <SelectField
          label="Dịch vụ khám"
          value={serviceId}
          onChange={(value) => {
            setServiceId(value);
            resetSlots();
          }}
          disabled={busy}
          options={[
            { value: "", label: "Chọn dịch vụ" },
            ...services.map((service) => ({
              value: service.id,
              label: `${service.name} · ${money(service.price)}`,
            })),
          ]}
        />
        <TextField
          label="Ngày khám"
          type="date"
          value={date}
          min={today()}
          onChange={(value) => {
            setDate(value);
            resetSlots();
          }}
          disabled={busy}
        />
        <SelectField
          label="Khung giờ còn trống"
          value={slotKey}
          onChange={setSlotKey}
          disabled={busy || slots.length === 0}
          options={[
            {
              value: "",
              label: slots.length === 0 ? "Chưa có giờ trống" : "Chọn giờ",
            },
            ...slots.map((slot) => ({
              value: slotValue(slot),
              label: `${clinicLocal(slot.startTime).slice(11, 16)} · ${slot.doctorDisplayName}`,
            })),
          ]}
        />
        <TextField
          label="Lý do khám (không bắt buộc)"
          value={purpose}
          onChange={setPurpose}
          disabled={busy}
        />
        {loadError && (
          <p role="alert" className="text-sm text-destructive">
            {loadError}
          </p>
        )}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={onClose}
          >
            Đóng
          </Button>
          <Button type="submit" disabled={!canSubmit}>
            {busy ? "Đang đặt lịch..." : "Đặt lịch khám"}
          </Button>
        </div>
      </form>
    </FormModal>
  );
}
