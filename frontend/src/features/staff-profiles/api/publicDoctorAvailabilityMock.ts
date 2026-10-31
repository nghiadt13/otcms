import { addDays } from "@/lib/format/formatDate";
import type { DoctorAvailabilityDay } from "../types/publicDoctor";

// Giả lập giờ còn nhận của bác sĩ trong 7 ngày tới (UC16 bước 4). Key là tên đăng nhập; số
// là độ lệch ngày (0 = hôm nay) mà bác sĩ hết giờ. Bác sĩ không có trong bảng còn giờ mọi
// ngày. BE thật tính từ ca làm trừ lịch đã đặt (BR-20).
const FULL_DAY_OFFSETS: Record<string, number[]> = {
  "bs.trung": [1, 4],
  "bs.minh": [3],
};

/** Tóm tắt 7 ngày bắt đầu từ `todayIso` (YYYY-MM-DD). */
export function buildDoctorAvailabilityMock(
  username: string,
  todayIso: string,
): DoctorAvailabilityDay[] {
  const fullOffsets = new Set(FULL_DAY_OFFSETS[username] ?? []);
  return Array.from({ length: 7 }, (_, offset) => ({
    date: addDays(todayIso, offset),
    hasOpenTime: !fullOffsets.has(offset),
  }));
}
