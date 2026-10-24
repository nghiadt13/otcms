import { mockFail } from "@/lib/api/mockServer";
import type { QueueEntryStatus, VisitStatus, VisitType } from "@/lib/domain/clinicVisit";
import { todayIsoDate } from "@/lib/format/formatDate";
import type { CheckInQueueEntry } from "../types/checkIn";
import type {
  ReceptionQueueResponse,
  ReceptionQueueRow,
  ReceptionQueueStatus,
  ReceptionQueueType,
  ReceptionRoomOption,
  ReceptionVisitSummary,
} from "../types/receptionQueue";
import { ageInYears } from "../schema/checkInPresentation";
import { SEED_APPOINTMENTS } from "./checkInMockAppointments";
import { ROOM_NAMES } from "./checkInMockData";
import { isToday, patientOrFail, toVietnamIso } from "./checkInMockState";
import { listCheckInQueueEntries } from "./receptionMockStore";
import { isReceptionQueueForcedEmpty } from "./receptionQueueMockControls";

// Đọc hàng đợi cho lớp API giả (chỉ api/receptionQueueApi.ts gọi). Nguồn là kho chung
// receptionMockStore. Thứ tự và vị trí do lớp giả tính như BE: nhóm ưu tiên trước, rồi giờ vào hàng
// (BR-29). FE không sắp lại. Hàng dịch vụ không đưa ra màn này.

const ROOMS: ReceptionRoomOption[] = ROOM_NAMES.map((name, index) => ({
  id: `room-k${index + 1}`,
  name,
}));

/** Thứ tự hiển thị theo trạng thái: đang chờ, đang phục vụ, vắng mặt. */
const STATUS_ORDER: Record<ReceptionQueueStatus, number> = { WAITING: 0, BEING_SERVED: 1, ABSENT: 2 };

/** Bản ghi khám hoặc đọc kết quả của hôm nay, kể cả đã xong (không tính hàng dịch vụ). */
type TodayEntry = CheckInQueueEntry & { queueType: ReceptionQueueType };

/** Bản ghi còn trong hàng: đang chờ, đang phục vụ hoặc vắng mặt. */
type ActiveEntry = TodayEntry & { status: ReceptionQueueStatus };

function isTodayEntry(entry: CheckInQueueEntry): entry is TodayEntry {
  return entry.queueType !== "SERVICE" && isToday(entry.checkedInAt);
}

function isActive(entry: TodayEntry): entry is ActiveEntry {
  const status: QueueEntryStatus = entry.status;
  return status === "WAITING" || status === "BEING_SERVED" || status === "ABSENT";
}

function todayEntries(): TodayEntry[] {
  return listCheckInQueueEntries().filter(isTodayEntry);
}

/** Vị trí 1-based trong hàng Đang chờ của từng phòng: nhóm ưu tiên trước, rồi giờ vào hàng. */
function waitingPositions(entries: ActiveEntry[]): Map<string, number> {
  const positions = new Map<string, number>();
  for (const room of ROOMS) {
    const line = entries
      .filter((entry) => entry.status === "WAITING" && entry.roomName === room.name)
      .sort(
        (a, b) =>
          Number(a.priorityLabel === null) - Number(b.priorityLabel === null) ||
          a.checkedInAt.localeCompare(b.checkedInAt) ||
          a.queueNumber - b.queueNumber,
      );
    line.forEach((entry, index) => positions.set(entry.id, index + 1));
  }
  return positions;
}

function roomByName(name: string): ReceptionRoomOption | undefined {
  return ROOMS.find((room) => room.name === name);
}

function appointmentStartOf(entry: TodayEntry): string | null {
  if (entry.appointmentId === null) return null;
  return SEED_APPOINTMENTS.find((seed) => seed.id === entry.appointmentId)?.startAt ?? null;
}

function toRow(
  entry: ActiveEntry,
  room: ReceptionRoomOption,
  position: number | null,
): ReceptionQueueRow {
  return {
    queueEntryId: entry.id,
    queueNumber: entry.queueNumber,
    position,
    patientName: entry.patientName,
    patientCode: entry.patientCode,
    roomId: room.id,
    roomName: room.name,
    queueType: entry.queueType,
    appointmentStartAt: appointmentStartOf(entry),
    priorityLabel: entry.priorityLabel,
    status: entry.status,
    joinedAt: entry.checkedInAt,
  };
}

export interface ReceptionQueueMockFilter {
  roomId?: string;
  statuses: readonly ReceptionQueueStatus[];
}

/**
 * GET queue. `counts.total` là số bệnh nhân đã tiếp nhận hôm nay (kể cả đã xong); đếm không theo bộ
 * lọc. Danh sách theo bộ lọc, xếp theo trạng thái, rồi phòng, rồi vị trí. Mã phòng sai → 400 MSG-39.
 */
export function getReceptionQueueMock(filter: ReceptionQueueMockFilter): ReceptionQueueResponse {
  const roomFilter =
    filter.roomId === undefined ? undefined : ROOMS.find((room) => room.id === filter.roomId);
  if (filter.roomId !== undefined && !roomFilter) mockFail(400, "MSG-39");

  const today: TodayEntry[] = isReceptionQueueForcedEmpty() ? [] : todayEntries();
  const active = today.filter(isActive);
  const positions = waitingPositions(active);
  const items = active
    .flatMap((entry) => {
      const room = roomByName(entry.roomName);
      return room ? [{ entry, room, position: positions.get(entry.id) ?? null }] : [];
    })
    .filter(
      ({ entry, room }) =>
        filter.statuses.includes(entry.status) && (!roomFilter || room.id === roomFilter.id),
    )
    .sort(
      (a, b) =>
        STATUS_ORDER[a.entry.status] - STATUS_ORDER[b.entry.status] ||
        ROOMS.indexOf(a.room) - ROOMS.indexOf(b.room) ||
        (a.position ?? 0) - (b.position ?? 0) ||
        a.entry.queueNumber - b.entry.queueNumber,
    )
    .map(({ entry, room, position }) => toRow(entry, room, position));

  return {
    items,
    counts: {
      total: today.length,
      waiting: active.filter((entry) => entry.status === "WAITING").length,
      absent: active.filter((entry) => entry.status === "ABSENT").length,
    },
    rooms: ROOMS.map((room) => ({ ...room })),
    generatedAt: toVietnamIso(new Date()),
  };
}

function visitTypeOf(entry: TodayEntry): VisitType {
  if (entry.appointmentId !== null) {
    return (
      SEED_APPOINTMENTS.find((seed) => seed.id === entry.appointmentId)?.visitType ?? "FIRST_VISIT"
    );
  }
  // Quay lại trong 7 ngày là tái khám của cùng lượt. Vãng lai: mock coi khám lần đầu (BE quyết theo lịch sử).
  return entry.queueType === "RESULT_REVIEW" ? "FOLLOW_UP" : "FIRST_VISIT";
}

/** Trạng thái lượt khám suy ra từ hàng đợi đang giữ bệnh nhân. */
function visitStatusOf(entry: ActiveEntry): VisitStatus {
  if (entry.queueType === "RESULT_REVIEW") return "AWAITING_CONCLUSION";
  return entry.status === "BEING_SERVED" ? "IN_EXAMINATION" : "WAITING_FOR_EXAMINATION";
}

/**
 * GET queue/{id}. Không có bản ghi → 404 MSG-38. Bản ghi đã rời hàng (xong, đã đóng, hoặc hàng
 * dịch vụ) → 410 MSG-38. Chỉ trả hành chính và hàng đợi, không có tiền sử hay chẩn đoán.
 */
export function getReceptionVisitSummaryMock(queueEntryId: string): ReceptionVisitSummary {
  const found = listCheckInQueueEntries().find((item) => item.id === queueEntryId);
  if (!found) mockFail(404, "MSG-38");
  if (!isTodayEntry(found) || !isActive(found)) mockFail(410, "MSG-38");

  const patient = patientOrFail(found.patientId);
  const position = waitingPositions(todayEntries().filter(isActive)).get(found.id);
  return {
    queueEntryId: found.id,
    queueNumber: found.queueNumber,
    roomName: found.roomName,
    patient: {
      fullName: patient.fullName,
      patientCode: patient.patientCode,
      sex: patient.sex,
      age: ageInYears(patient.dateOfBirth, todayIsoDate()),
    },
    checkedInAt: found.checkedInAt,
    appointmentStartAt: appointmentStartOf(found),
    visitType: visitTypeOf(found),
    responsibleDoctorName: found.doctorName,
    priorityLabel: found.priorityLabel,
    queueType: found.queueType,
    queueStatus: found.status,
    peopleAhead: found.status === "WAITING" && position !== undefined ? position - 1 : null,
    visitStatus: visitStatusOf(found),
  };
}
