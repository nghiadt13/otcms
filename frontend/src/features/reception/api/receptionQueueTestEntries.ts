import { at, MOCK_TODAY } from "./checkInMockData";
import { addCheckInQueueEntry, findQueueEntryByAppointment } from "./receptionMockStore";

// Dựng dữ liệu cho test của lớp API hàng đợi: thêm bản ghi như tiếp nhận vừa làm, và tra id bản ghi
// theo lịch hẹn trong kho giả. Chỉ dùng trong test.

/** Id bản ghi hàng đợi của một lịch hẹn đã tiếp nhận trong kho giả mặc định. */
export function queueIdOf(appointmentId: string): string {
  const entry = findQueueEntryByAppointment(appointmentId);
  if (!entry) throw new Error(`Thiếu bản ghi hàng đợi của ${appointmentId} trong kho giả.`);
  return entry.id;
}

export interface TestQueueEntry {
  patientName: string;
  roomName: string;
  /** Giờ HH:mm hôm nay (giờ Việt Nam). */
  time: string;
  priorityLabel?: string;
  queueType?: "EXAMINATION" | "SERVICE";
}

/** Thêm bản ghi hàng đợi như tiếp nhận vãng lai vừa làm (kho chung, số thứ tự nối tiếp). */
export function addTestEntry(entry: TestQueueEntry) {
  return addCheckInQueueEntry({
    visitId: `vis-test-${entry.time}`,
    patientId: "pat-p01",
    patientName: entry.patientName,
    patientCode: "BN-000301",
    appointmentId: null,
    queueType: entry.queueType ?? "EXAMINATION",
    roomName: entry.roomName,
    doctorName: "BS. Phạm Thu Hà",
    priorityLabel: entry.priorityLabel ?? null,
    checkedInAt: at(MOCK_TODAY, entry.time),
  });
}
