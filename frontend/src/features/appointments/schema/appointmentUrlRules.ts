import { addDays } from "@/lib/format/formatDate";
import type {
  AppointmentStatus,
  AppointmentUrlState,
} from "../types/appointment";

/** Tab giờ trống xem tối đa 7 ngày. */
export const MAX_AVAILABILITY_DAYS = 7;
/** Khoảng mặc định của tab danh sách: hôm nay → +7 ngày. */
export const DEFAULT_LIST_SPAN_DAYS = 7;

export const STATUS_VALUES: AppointmentStatus[] = [
  "BOOKED",
  "CHECKED_IN",
  "CANCELLED",
  "NO_SHOW",
];

type ParamReader = { get(name: string): string | null };

function validDate(value: string | null): string | undefined {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value
    ? undefined
    : value;
}

/**
 * Đọc URL → trạng thái. `date` là đầu vào tương đương `from=to`; chỉ có một trong
 * `from`/`to` thì đầu còn lại bằng nó; tab lạ về `list`; ở tab giờ trống, khoảng
 * hợp lệ dài hơn 7 ngày bị cắt còn `from + 6`.
 */
export function parseAppointmentUrl(params: ParamReader): AppointmentUrlState {
  const tab = params.get("tab") === "availability" ? "availability" : "list";
  const date = validDate(params.get("date"));
  let from = validDate(params.get("from")) ?? date;
  let to = validDate(params.get("to")) ?? date;
  if (from && !to) to = from;
  if (to && !from) from = to;
  if (tab === "availability" && from && to && from <= to) {
    const last = addDays(from, MAX_AVAILABILITY_DAYS - 1);
    if (to > last) to = last;
  }
  const status = params.get("status") as AppointmentStatus | null;
  return {
    tab,
    doctorId: params.get("doctorId") || undefined,
    from,
    to,
    status: status && STATUS_VALUES.includes(status) ? status : undefined,
  };
}

/** Trạng thái → query string. Không bao giờ có `q` hay `page` (dữ liệu cá nhân / trạng thái tạm). */
export function buildAppointmentQuery(state: AppointmentUrlState): string {
  const search = new URLSearchParams();
  if (state.tab === "availability") search.set("tab", "availability");
  if (state.doctorId) search.set("doctorId", state.doctorId);
  if (state.from) search.set("from", state.from);
  if (state.to) search.set("to", state.to);
  if (state.status) search.set("status", state.status);
  const text = search.toString();
  return text ? `?${text}` : "";
}

/** Khoảng ngày thực dùng cho tab danh sách (mặc định khi URL không ghi). */
export function resolveListRange(
  state: Pick<AppointmentUrlState, "from" | "to">,
  today: string,
): { from: string; to: string } {
  const from = state.from ?? today;
  return { from, to: state.to ?? (state.from ? from : addDays(from, DEFAULT_LIST_SPAN_DAYS)) };
}

/** Khoảng ngày thực dùng cho tab giờ trống: mặc định một ngày (hôm nay), tối đa 7 ngày. */
export function resolveAvailabilityRange(
  state: Pick<AppointmentUrlState, "from" | "to">,
  today: string,
): { from: string; to: string } {
  const from = state.from ?? today;
  const to = state.to ?? from;
  const last = addDays(from, MAX_AVAILABILITY_DAYS - 1);
  return { from, to: to > last && from <= to ? last : to };
}

/** "MSG-54" khi bắt đầu sau kết thúc; ngược lại null. */
export function rangeErrorCode(from?: string, to?: string): "MSG-54" | null {
  return from && to && from > to ? "MSG-54" : null;
}
