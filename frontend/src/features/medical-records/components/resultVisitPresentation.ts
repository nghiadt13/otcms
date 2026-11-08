import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type {
  ResultVisitStatus,
  ResultVisitSummary,
} from "../types/examinationResult";

// Chữ và nhóm hiển thị của danh sách kết quả khám. Không có luật nghiệp vụ: chỉ đổi dữ liệu BE
// thành nhóm tháng, bộ đếm và khoảng thời gian lọc ở phía giao diện.

/** Nhãn trạng thái ở màn bệnh nhân: lượt đã công bố kết luận và lượt chưa hoàn tất. */
export const RESULT_STATUS_LABELS: Record<ResultVisitStatus, string> = {
  COMPLETED: "Có kết quả",
  INCOMPLETE: "Đang xử lý",
};

export type ResultTab = "ALL" | "COMPLETED" | "INCOMPLETE";

export type ResultTimeRange =
  "ALL" | "LAST_30_DAYS" | "LAST_90_DAYS" | "THIS_YEAR";

export const RESULT_TIME_RANGES: { value: ResultTimeRange; label: string }[] = [
  { value: "ALL", label: "Tất cả thời gian" },
  { value: "LAST_30_DAYS", label: "30 ngày qua" },
  { value: "LAST_90_DAYS", label: "90 ngày qua" },
  { value: "THIS_YEAR", label: "Năm nay" },
];

/** Lượt khám có nằm trong khoảng thời gian đã chọn không (`visitDate` dạng yyyy-MM-dd). */
export function isInTimeRange(
  visitDate: string,
  range: ResultTimeRange,
  today: string = todayIsoDate(),
): boolean {
  switch (range) {
    case "ALL":
      return true;
    case "LAST_30_DAYS":
      return visitDate >= addDays(today, -30);
    case "LAST_90_DAYS":
      return visitDate >= addDays(today, -90);
    case "THIS_YEAR":
      return visitDate.slice(0, 4) === today.slice(0, 4);
  }
}

export interface ResultCounts {
  total: number;
  completed: number;
  incomplete: number;
}

export function countResults(items: ResultVisitSummary[]): ResultCounts {
  const completed = items.filter((item) => item.status === "COMPLETED").length;
  return {
    total: items.length,
    completed,
    incomplete: items.length - completed,
  };
}

export function filterByTab(
  items: ResultVisitSummary[],
  tab: ResultTab,
): ResultVisitSummary[] {
  return tab === "ALL" ? items : items.filter((item) => item.status === tab);
}

export interface ResultMonthGroup {
  /** yyyy-MM */
  key: string;
  /** "Tháng 10, 2026" */
  label: string;
  items: ResultVisitSummary[];
}

/** Nhóm theo tháng, tháng mới nhất trước; trong tháng cũng mới nhất trước. */
export function groupByMonth(items: ResultVisitSummary[]): ResultMonthGroup[] {
  const sorted = [...items].sort((a, b) =>
    b.visitDate.localeCompare(a.visitDate),
  );
  const groups: ResultMonthGroup[] = [];
  for (const item of sorted) {
    const key = item.visitDate.slice(0, 7);
    let group = groups.find((candidate) => candidate.key === key);
    if (!group) {
      group = {
        key,
        label: `Tháng ${Number(key.slice(5, 7))}, ${key.slice(0, 4)}`,
        items: [],
      };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
}

const WEEKDAY = new Intl.DateTimeFormat("vi-VN", {
  weekday: "long",
  timeZone: "Asia/Ho_Chi_Minh",
});

/** Ô ngày của thẻ: { month: "10", day: "23", weekday: "Thứ Sáu" }. */
export function visitDateParts(visitDate: string): {
  month: string;
  day: string;
  weekday: string;
} {
  const weekday = WEEKDAY.format(new Date(`${visitDate}T00:00:00+07:00`))
    .split(" ")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
  return {
    month: String(Number(visitDate.slice(5, 7))),
    day: String(Number(visitDate.slice(8, 10))),
    weekday,
  };
}
