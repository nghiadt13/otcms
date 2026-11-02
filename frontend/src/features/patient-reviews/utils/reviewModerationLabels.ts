import type { StatusTone } from "@/components/admin/StatusBadge";
import type {
  ReviewAction,
  ReviewCounts,
  ReviewFilters,
  ReviewStatus,
} from "../types/reviewModeration";

/** Giá trị "tất cả" ở UI (tab, ToggleGroup); ToggleGroup coi value rỗng là "không chọn". */
export const ALL_VALUE = "ALL";
export const toUiValue = (value: string) => value || ALL_VALUE;
export const fromUiValue = (value: string | undefined) =>
  !value || value === ALL_VALUE ? "" : value;

export const STATUS_TABS: { value: string; label: string }[] = [
  { value: "PENDING", label: "Chờ duyệt" },
  { value: "PUBLISHED", label: "Đang hiển thị" },
  { value: "HIDDEN", label: "Đã ẩn" },
  { value: ALL_VALUE, label: "Tất cả" },
];

export function countForTab(tab: string, counts: ReviewCounts): number {
  switch (tab) {
    case "PENDING":
      return counts.pending;
    case "PUBLISHED":
      return counts.published;
    case "HIDDEN":
      return counts.hidden;
    default:
      return counts.all;
  }
}

export const STATUS_LABEL: Record<ReviewStatus, string> = {
  PENDING: "Chờ duyệt",
  PUBLISHED: "Đang hiển thị",
  HIDDEN: "Đã ẩn",
};

export const STATUS_TONE: Record<ReviewStatus, StatusTone> = {
  PENDING: "warning",
  PUBLISHED: "success",
  HIDDEN: "neutral",
};

/** Nhãn nút quyết định. "Từ chối" khi chờ duyệt, "Ẩn đánh giá" khi đang hiển thị. */
export function decisionLabel(
  action: Extract<ReviewAction, "approve" | "hide" | "unhide">,
  status: ReviewStatus,
): { label: string; pending: string } {
  if (action === "approve")
    return { label: "Duyệt và hiển thị", pending: "Đang duyệt..." };
  if (action === "unhide")
    return { label: "Hiện lại", pending: "Đang hiện lại..." };
  return status === "PENDING"
    ? { label: "Từ chối", pending: "Đang ẩn..." }
    : { label: "Ẩn đánh giá", pending: "Đang ẩn..." };
}

export function hasExtraFilters(filters: ReviewFilters): boolean {
  return Boolean(
    filters.rating || filters.replied || filters.from || filters.to,
  );
}

/** Câu trạng thái trống dạy cách dùng, khác nhau theo tab và theo việc có đang lọc. */
export function emptyTextFor(
  status: ReviewFilters["status"],
  filtered: boolean,
) {
  if (filtered)
    return "Không có đánh giá khớp bộ lọc. Thử đổi khoảng ngày hoặc bỏ bớt bộ lọc.";
  switch (status) {
    case "PENDING":
      return "Chưa có đánh giá nào chờ duyệt. Đánh giá mới của bệnh nhân sẽ hiện ở đây sau lượt khám.";
    case "PUBLISHED":
      return "Chưa có đánh giá nào đang hiển thị. Duyệt đánh giá ở tab Chờ duyệt để đưa lên website.";
    case "HIDDEN":
      return "Chưa có đánh giá nào bị ẩn.";
    default:
      return "Chưa có đánh giá nào. Đánh giá sẽ hiện ở đây sau khi bệnh nhân gửi sau lượt khám.";
  }
}
