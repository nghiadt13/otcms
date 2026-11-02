import { addDays } from "@/lib/format/formatDate";
import type { ServiceOption, UnpaidItemDetail } from "../types/unpaidItem";

// Dữ liệu giả cho khoản chưa thanh toán. Mọi tên, SĐT đều giả.
// 26 khoản (2 trang); gồm phí khám (lịch hẹn LH-…) và chỉ định (lượt khám LK-…).

const CONSULTATION_FEE_VND = 150000;

/** Nguồn tạm cho ô Dịch vụ; thay bằng danh sách dịch vụ của phase 07 khi có. */
export const MOCK_SERVICE_OPTIONS: (ServiceOption & { priceVnd: number })[] = [
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a01",
    name: "Chụp X-quang cột sống thắt lưng",
    priceVnd: 250000,
  },
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a02",
    name: "Siêu âm khớp gối",
    priceVnd: 200000,
  },
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a03",
    name: "Xét nghiệm công thức máu",
    priceVnd: 120000,
  },
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a04",
    name: "Chụp CT khớp vai",
    priceVnd: 650000,
  },
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a05",
    name: "Đo mật độ xương",
    priceVnd: 250000,
  },
  {
    id: "c3a1f0e2-5b7d-4e8a-9f12-6d4b2a1c0a06",
    name: "Điện tim",
    priceVnd: 130000,
  },
];

const PATIENTS = [
  "Nguyễn Thị Hoa",
  "Trần Văn Minh",
  "Lê Thị Bích",
  "Phạm Quốc Hùng",
  "Hoàng Thị Lan",
  "Vũ Đình Nam",
  "Đặng Thu Phương",
  "Bùi Văn Quân",
  "Ngô Thị Sen",
  "Đỗ Minh Tuấn",
  "Lý Thanh Vân",
  "Phan Văn Xuân",
];
const LONG_NAME = "Nguyễn Thị Hồng Ngọc Bảo Châu Phương Thảo Vy";
const DOCTORS = ["BS. Lê Quang Huy", "BS. Trần Thu Hiền", "BS. Phạm Đức Long"];

/** Id đặc biệt kích hoạt lỗi giả của chi tiết (kết thúc bằng 0404 / 0403). */
export const UNPAID_ITEM_FORBIDDEN_ID = "0f3c2a10-7d4e-4b8a-9c11-2a6f5e000403";
export const UNPAID_ITEM_NOT_FOUND_ID = "0f3c2a10-7d4e-4b8a-9c11-2a6f5e000404";
/** `q` này kích hoạt lỗi dịch vụ giả của danh sách. */
export const UNPAID_ITEM_ERROR_QUERY = "loi-may-chu";

const TOTAL = 26;
const OUT_OF_PERIOD_INDEX = 7; // ngày 2026-09-28, để thử khoảng ngày
const NO_ACTION_INDEX = 13; // allowedActions rỗng → không có nút Xem
const NO_PHONE_INDEX = 4;
const LONG_NAME_INDEX = 10;

function buildItem(i: number): UnpaidItemDetail {
  const isFee = i % 3 === 0;
  const service = MOCK_SERVICE_OPTIONS[i % MOCK_SERVICE_OPTIONS.length];
  const day =
    i === OUT_OF_PERIOD_INDEX
      ? "2026-09-28"
      : addDays("2026-10-01", Math.floor((i * 19) / (TOTAL - 1)));
  const hour = String(8 + ((i * 3) % 9)).padStart(2, "0");
  const minute = String((i * 11) % 60).padStart(2, "0");
  const seq = String(i + 1).padStart(4, "0");
  return {
    id: `0f3c2a10-7d4e-4b8a-9c11-2a6f5e00${seq}`,
    kind: isFee ? "CONSULTATION_FEE" : "SERVICE_ORDER",
    itemName: isFee ? "Phí khám" : service.name,
    serviceId: isFee ? null : service.id,
    priceVnd: isFee ? CONSULTATION_FEE_VND : service.priceVnd,
    orderedAt: `${day}T${hour}:${minute}:00+07:00`,
    patient: {
      fullName:
        i === LONG_NAME_INDEX ? LONG_NAME : PATIENTS[i % PATIENTS.length],
      phone:
        i === NO_PHONE_INDEX
          ? null
          : `0901${String(230000 + i * 137).padStart(6, "0")}`,
    },
    reference: isFee
      ? {
          type: "APPOINTMENT",
          code: `LH-2610-${String(100 + i).padStart(4, "0")}`,
          date: addDays(day, 1),
        }
      : {
          type: "VISIT",
          code: `LK-2610-${String(40 + i).padStart(4, "0")}`,
          date: day,
        },
    orderedByDoctorName: isFee ? null : DOCTORS[i % DOCTORS.length],
    allowedActions: i === NO_ACTION_INDEX ? [] : ["view"],
  };
}

export const UNPAID_ITEM_SEED: UnpaidItemDetail[] = Array.from(
  { length: TOTAL },
  (_, i) => buildItem(i),
);

let items: UnpaidItemDetail[] = structuredClone(UNPAID_ITEM_SEED);

/** Kho giả có thể sửa: test thay nội dung bằng `resetUnpaidItemMockStore([])` để thử trạng thái trống. */
export const unpaidItemMockStore = {
  get items() {
    return items;
  },
};

export function resetUnpaidItemMockStore(
  next: UnpaidItemDetail[] = UNPAID_ITEM_SEED,
) {
  items = structuredClone(next);
}
