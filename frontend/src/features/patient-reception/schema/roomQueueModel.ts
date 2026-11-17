import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import type { RoomQueueEntry } from "../api/receptionLiveApi";

// Dữ liệu hàng chờ theo phòng: chọn phòng từ các lượt hôm nay, tách hàng của phòng thành nhóm hiển
// thị. Không có luật xếp hàng ở đây: thứ tự và trạng thái do BE trả, FE chỉ nhóm lại.

export type RoomOption = { id: string; label: string };

/** Các phòng có lượt hôm nay (theo tên phòng). Lượt chưa gắn phòng không tạo phòng. */
export function roomOptionsOf(items: ExamQueueItem[]): RoomOption[] {
  const rooms = new Map<string, string>();
  for (const item of items) {
    if (item.roomId && !rooms.has(item.roomId))
      rooms.set(item.roomId, item.roomLabel ?? "Phòng chưa đặt tên");
  }
  return [...rooms]
    .map(([id, label]) => ({ id, label }))
    .sort((a, b) => a.label.localeCompare(b.label, "vi", { numeric: true }));
}

/**
 * Hàng của phòng tách theo nhóm, giữ thứ tự BE. `serving`: đang được gọi hoặc đang khám; `waiting`:
 * đang chờ; `absent`: vắng (có thể đưa lại); `awaiting`: chờ kết luận (chỉ xem).
 */
export function splitRoomEntries(entries: RoomQueueEntry[]) {
  const pick = (...statuses: string[]) =>
    entries.filter((entry) => statuses.includes(entry.status));
  return {
    serving: pick("CALLED", "IN_EXAMINATION"),
    waiting: pick("WAITING"),
    absent: pick("ABSENT"),
    awaiting: pick("AWAITING_CONCLUSION"),
  };
}

/** Tra lượt theo mã lượt khám để lấy tên bệnh nhân (hàng của phòng không có tên). */
export function itemsByVisit(items: ExamQueueItem[]) {
  return new Map(items.map((item) => [item.visitId, item]));
}
