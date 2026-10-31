import { formatAppMessage } from "@/lib/messages/appMessages";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { ReceptionCommand, ReceptionState } from "../types";

// Thông báo sau một lệnh lễ tân thành công, lấy nguyên câu từ bảng MSG của R3 (không tự đặt
// câu). `null`: R3 không định nghĩa thông báo cho thao tác đó, màn tự cập nhật trạng thái.
export type ReceptionFeedback = {
  tone: "success" | "warning" | "error";
  text: string;
};

const success = (text: string): ReceptionFeedback => ({
  tone: "success",
  text,
});

/** MSG-94: số thứ tự và phòng của các entry vừa tạo cho bệnh nhân trong ngày. */
function checkInFeedback(patientId: string, next: ReceptionState) {
  const visitIds = new Set(
    next.visits.filter((v) => v.patientId === patientId).map((v) => v.id),
  );
  const date = next.clock.slice(0, 10);
  const entries = next.queues.filter(
    (q) => visitIds.has(q.visitId) && q.date === date && q.status === "waiting",
  );
  const room = (id: string) => next.rooms.find((r) => r.id === id)?.name ?? id;
  return success(
    formatAppMessage("MSG-94", {
      number: entries.map((q) => q.number).join(", "),
      room: entries.map((q) => room(q.roomId)).join(", "),
    }),
  );
}

export function receptionFeedback(
  command: ReceptionCommand,
  next: ReceptionState,
): ReceptionFeedback | null {
  switch (command.type) {
    case "save-patient":
    case "confirm-link":
      return success(formatAppMessage("MSG-90"));
    case "book":
      return success(formatAppMessage("MSG-26"));
    case "reassign": {
      const doctorId = next.appointments.find(
        (a) => a.id === command.id,
      )?.doctorId;
      const doctor = next.doctors.find((d) => d.id === doctorId)?.name ?? "";
      return success(formatAppMessage("MSG-27", { doctor }));
    }
    case "check-in":
      return checkInFeedback(command.patientId, next);
    case "call-next":
    case "queue-action":
    case "reorder":
    case "move-room":
    case "transfer-doctor":
      return success(formatAppMessage("MSG-43"));
    case "pay-cash":
      return success(
        formatAppMessage("MSG-48", {
          amount: formatCurrencyVnd(next.payments[0]?.amount ?? 0),
        }),
      );
    case "start-qr":
      return { tone: "warning", text: formatAppMessage("MSG-52") };
    case "demo-gateway": {
      const tx = next.transactions.find((t) => t.id === command.id);
      if (tx?.status === "succeeded")
        return success(
          formatAppMessage("MSG-48", { amount: formatCurrencyVnd(tx.amount) }),
        );
      if (tx?.needsReconciliation)
        return { tone: "warning", text: formatAppMessage("MSG-52") };
      return { tone: "error", text: formatAppMessage("MSG-53") };
    }
    case "read-notice":
      return success(formatAppMessage("MSG-60"));
    case "avatar":
      return success(formatAppMessage("MSG-05"));
    case "notify-unavailable":
    case "prepare-walk-in":
      return null;
  }
}
