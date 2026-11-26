"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import { MyAccountProfile } from "@/features/my-account/components/MyAccountProfile";
import { PatientNotificationsScreen } from "@/features/notifications/components/PatientNotificationsScreen";
import type { NotificationRecordHrefResolver } from "@/features/notifications/utils/notificationRecordLinks";
import { CancelAppointmentWithReasonDialog } from "@/features/appointments/components/CancelAppointmentWithReasonDialog";
import { RescheduleAppointmentDialog } from "@/features/appointments/components/RescheduleAppointmentDialog";
import {
  cancelReceptionAppointment,
  getReceptionRescheduleAvailability,
  rescheduleReceptionAppointment,
  type ReceptionAppointment,
} from "@/features/patient-reception/api/receptionLiveApi";
import { ReceptionScreen } from "@/features/patient-reception/components/ReceptionScreen";
import type { ReceptionSection } from "@/features/patient-reception/navigation";

const resolveReceptionNotificationHref: NotificationRecordHrefResolver = (
  record,
) => {
  switch (record.kind) {
    case "APPOINTMENT":
      return "/reception/appointments";
    case "VISIT":
      return "/reception/visits";
    case "VISIT_RESULT":
      return "/reception/results";
    case "PAYMENT":
      return "/reception/payment-history";
    default:
      return null;
  }
};

function ReceptionNotifications() {
  return (
    <PatientNotificationsScreen
      resolveRecordHref={resolveReceptionNotificationHref}
    />
  );
}

function AppointmentActions({
  appointment,
  onChanged,
}: {
  appointment: ReceptionAppointment;
  onChanged: () => void;
}) {
  const [action, setAction] = useState<"cancel" | "reschedule" | null>(null);
  const loadSlots = useCallback(
    (date: string, signal: AbortSignal) =>
      getReceptionRescheduleAvailability(
        appointment.id,
        date,
        date,
        signal,
      ).then((result) => result.slots),
    [appointment.id],
  );
  return (
    <div className="my-3 flex gap-2">
      <Button onClick={() => setAction("reschedule")}>Đổi giờ</Button>
      <Button variant="outline" onClick={() => setAction("cancel")}>
        Hủy lịch
      </Button>
      <CancelAppointmentWithReasonDialog
        open={action === "cancel"}
        onClose={() => setAction(null)}
        onConfirm={async (reason, clinicFault) => {
          await cancelReceptionAppointment({
            appointmentId: appointment.id,
            reason,
            clinicFault,
          });
          onChanged();
        }}
      />
      <RescheduleAppointmentDialog
        currentDoctorId={appointment.doctorId}
        open={action === "reschedule"}
        onClose={() => setAction(null)}
        loadSlots={loadSlots}
        onConfirm={async (slot, reason) => {
          await rescheduleReceptionAppointment({
            appointmentId: appointment.id,
            doctorId: slot.doctorId,
            startTime: slot.startTime,
            reason,
          });
          onChanged();
        }}
      />
    </div>
  );
}

export function ReceptionScreenComposition({
  section,
}: {
  section: ReceptionSection;
}) {
  return (
    <ReceptionScreen
      section={section}
      appointmentActions={AppointmentActions}
      accountComponent={MyAccountProfile}
      notificationsComponent={ReceptionNotifications}
    />
  );
}
