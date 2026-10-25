import { addDays } from "@/lib/format/formatDate";
import { sumAmounts } from "./billingMockHelpers";
import type {
  PaymentDetail,
  PaymentLine,
  PaymentListItem,
  PaymentMethod,
} from "../types/payment";

// Dữ liệu giả cho lịch sử thanh toán: 46 phiếu thu PT-2026-000481 … 000526.
// 44 phiếu trong kỳ mặc định (2026-09-20 → 2026-10-20), 2 phiếu ngày 2026-09-10 (ngoài kỳ).
// Mọi tên, mã giao dịch đều giả. Sinh bằng vòng lặp để mọi tổng luôn cân đối.

/** "Hôm nay" cố định của dữ liệu giả để test ổn định, không dùng Date.now(). */
export const MOCK_TODAY = "2026-10-20";
/** Id kích hoạt lỗi chi tiết giả (giống BE: ngoài quyền / lỗi dịch vụ). */
export const PAYMENT_FORBIDDEN_ID = "00000000-0000-4000-8000-000000009403";
export const PAYMENT_ERROR_ID = "00000000-0000-4000-8000-000000009500";

const EXAM_FEE: PaymentLine = { name: "Phí khám", price: 150000 };
const SERVICES: PaymentLine[] = [
  { name: "Xét nghiệm công thức máu", price: 120000 },
  { name: "Chụp X-quang cột sống thắt lưng", price: 180000 },
  { name: "Siêu âm khớp gối", price: 200000 },
  { name: "Xét nghiệm đường huyết", price: 90000 },
  { name: "Đo mật độ xương", price: 250000 },
  { name: "Điện tim", price: 130000 },
  { name: "Chụp CT khớp vai", price: 650000 },
];
const PATIENTS = [
  "Nguyễn Văn An", "Trần Quốc Bảo", "Phạm Thị Cúc", "Hoàng Minh Đức",
  "Vũ Thị Hạnh", "Đặng Văn Khoa", "Bùi Thanh Lan", "Ngô Quang Minh",
  "Đỗ Thị Ngọc", "Lý Văn Phát", "Dương Thị Quyên", "Phan Văn Sơn",
  "Trịnh Thu Trang", "Huỳnh Văn Út",
];
const LONG_NAME = "Nguyễn Hoàng Thiên Phúc Anh Khoa Minh Quân";
const CASHIERS = ["Trần Thị Hòa", "Phạm Thu Hà", "Võ Minh Châu"];

// Ca bắt buộc: VNPay chỉ có phí khám; tiền mặt 6 dòng; không có phí khám.
const SPECIAL_LINES: Record<number, PaymentLine[]> = {
  0: [EXAM_FEE, SERVICES[0], SERVICES[1]],
  1: [EXAM_FEE],
  2: [EXAM_FEE, ...SERVICES.slice(0, 5)],
  3: [SERVICES[1], SERVICES[3]],
};

const TOTAL = 46;
const IN_RANGE = 44;

function linesFor(i: number): PaymentLine[] {
  if (SPECIAL_LINES[i]) return SPECIAL_LINES[i];
  const services = Array.from(
    { length: i % 4 },
    (_, k) => SERVICES[(i + k * 2) % SERVICES.length],
  );
  return [EXAM_FEE, ...services];
}

function methodFor(i: number): PaymentMethod {
  if (i === 1) return "VNPAY";
  return i % 5 < 3 ? "CASH" : "VNPAY";
}

function patientFor(i: number): string {
  if (i === 1 || i === 20) return "Lê Thị Bích"; // hai phiếu cùng tên, khác ngày
  if (i === 4) return LONG_NAME;
  return PATIENTS[i % PATIENTS.length];
}

function paidAtFor(i: number): string {
  const day =
    i < IN_RANGE
      ? addDays("2026-09-20", Math.floor((i * 30) / (IN_RANGE - 1)))
      : "2026-09-10";
  const hour = String(8 + ((i * 5) % 10)).padStart(2, "0");
  const minute = String((i * 17) % 60).padStart(2, "0");
  return `${day}T${hour}:${minute}:00+07:00`;
}

function buildPayment(i: number): PaymentDetail {
  const lines = linesFor(i);
  const method = methodFor(i);
  const number = 481 + i;
  return {
    id: `00000000-0000-4000-8000-${String(number).padStart(12, "0")}`,
    receiptNumber: `PT-2026-${String(number).padStart(6, "0")}`,
    paidAt: paidAtFor(i),
    patientName: patientFor(i),
    amount: sumAmounts(lines, (line) => line.price),
    method,
    cashierName: method === "CASH" ? CASHIERS[i % CASHIERS.length] : null,
    vnpayTransactionCode:
      method === "VNPAY" ? (i === 1 ? "14872035" : String(14872000 + i)) : null,
    lines,
    allowedActions: ["view"],
  };
}

export const PAYMENT_SEED: PaymentDetail[] = Array.from(
  { length: TOTAL },
  (_, i) => buildPayment(i),
);

/** "Phí khám + 2 chỉ định" / "Phí khám" / "2 chỉ định". */
export function summarizeLines(lines: PaymentLine[]): string {
  const hasExam = lines.some((line) => line.name === EXAM_FEE.name);
  const orders = lines.length - (hasExam ? 1 : 0);
  if (!hasExam) return `${orders} chỉ định`;
  return orders > 0 ? `${EXAM_FEE.name} + ${orders} chỉ định` : EXAM_FEE.name;
}

export function toPaymentListItem(detail: PaymentDetail): PaymentListItem {
  const { lines, vnpayTransactionCode, ...rest } = detail;
  void vnpayTransactionCode; // chỉ có ở chi tiết
  return { ...rest, itemSummary: summarizeLines(lines) };
}
