import type { CareRelationship } from "@/lib/domain/careRelationship";
import type { PaymentMethod } from "@/lib/domain/clinicVisit";
import type {
  PaymentItemStatus,
  PaymentSourceKind,
} from "../types/patientBilling";

// Dữ liệu giả khu bệnh nhân (thanh toán). Id hồ sơ khớp kho hồ sơ đã gắn
// (lib/patient-scope/linkedProfilesMockStore.ts). Lịch hẹn và lượt khám của phase 04/05 chưa
// có feature, nên kho này giữ các khoản riêng với id theo kịch bản phase 06 §8.

export interface SeedPatient {
  id: string;
  fullName: string;
  relationship: CareRelationship;
}

export const SEED_PATIENTS = {
  an: {
    id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
    fullName: "Nguyễn Văn An",
    relationship: "SELF",
  },
  lan: {
    id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
    fullName: "Nguyễn Thị Lan",
    relationship: "PARENT",
  },
  huy: {
    id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f03",
    fullName: "Nguyễn Gia Huy",
    relationship: "CHILD",
  },
} satisfies Record<string, SeedPatient>;

export type SeedPatientKey = keyof typeof SEED_PATIENTS;

/** Nguồn không thuộc phạm vi tài khoản: mọi thao tác trả 403 MSG-40. */
export const FORBIDDEN_SOURCE_ID = "vis-forbidden";
/** Chỉ định này: lần kiểm đầu và lần kế tiếp còn PENDING, lần thứ ba thành công (kịch bản phase 06 §8). */
export const PENDING_TWICE_ITEM_ID = "svc-pending";

export interface SeedItem {
  id: string;
  label: string;
  amount: number;
  status: PaymentItemStatus;
  cancellationReason?: string;
  receipt?: { paymentId: string; receiptNo: string };
  /** Phòng phục vụ chỉ định; có khi khoản là chỉ định dịch vụ (cấp số thứ tự khi trả). */
  room?: string;
}

export interface SeedSource {
  kind: PaymentSourceKind;
  id: string;
  /** Lượt khám: yyyy-MM-dd. Lịch hẹn: ISO có offset +07:00. */
  date: string;
  patient: SeedPatientKey;
  items: SeedItem[];
}

export const SEED_SOURCES: SeedSource[] = [
  {
    kind: "visit",
    id: "vis-an-today",
    date: "2026-10-23",
    patient: "an",
    items: [
      {
        id: PENDING_TWICE_ITEM_ID,
        label: "MRI cột sống thắt lưng",
        amount: 1200000,
        status: "PENDING_PAYMENT",
        room: "Phòng MRI 1",
      },
      {
        id: "svc-cbc",
        label: "Công thức máu",
        amount: 250000,
        status: "PENDING_PAYMENT",
        room: "Phòng xét nghiệm 2",
      },
      {
        id: "svc-xray-knee",
        label: "X-quang khớp gối",
        amount: 200000,
        status: "PAID",
        receipt: { paymentId: "pm-1029", receiptNo: "PT-1029" },
      },
      {
        id: "svc-us-shoulder",
        label: "Siêu âm khớp vai",
        amount: 300000,
        status: "CANCELLED",
        cancellationReason: "Bác sĩ thay bằng MRI",
      },
    ],
  },
  {
    kind: "visit",
    id: "vis-lan-await",
    date: "2026-10-22",
    patient: "lan",
    items: [
      // 1.000.000 đồng: tổng chẵn triệu, kịch bản kết quả FAILED.
      {
        id: "svc-endoscopy",
        label: "Nội soi dạ dày",
        amount: 1000000,
        status: "PENDING_PAYMENT",
        room: "Phòng nội soi 1",
      },
    ],
  },
  {
    kind: "appointment",
    id: "apt-lan-27",
    date: "2026-10-27T14:00:00+07:00",
    patient: "lan",
    items: [
      {
        id: "fee-lan-27",
        label: "Phí khám",
        amount: 200000,
        status: "PENDING_PAYMENT",
      },
    ],
  },
];

/** Một dòng lịch sử đã hoàn tất: [mã, thời điểm ISO +07:00, người khám, phương thức, [khoản, số tiền][]]. */
export type SeedHistoryRow = [
  id: string,
  paidAt: string,
  patient: SeedPatientKey,
  method: PaymentMethod,
  lines: [label: string, amount: number][],
];

/** 14 thanh toán (tiền mặt và VNPay) trong 60 ngày gần đây (kịch bản phase 06 §8). */
export const SEED_HISTORY: SeedHistoryRow[] = [
  [
    "pm-1029",
    "2026-10-23T10:20:00+07:00",
    "an",
    "CASH",
    [["X-quang khớp gối", 200000]],
  ],
  [
    "pm-1013",
    "2026-10-18T09:05:00+07:00",
    "an",
    "VNPAY",
    [["Phí khám", 200000]],
  ],
  [
    "pm-1012",
    "2026-10-15T14:30:00+07:00",
    "lan",
    "CASH",
    [["Siêu âm bụng tổng quát", 300000]],
  ],
  [
    "pm-1011",
    "2026-10-12T08:45:00+07:00",
    "huy",
    "CASH",
    [["Phí khám nhi", 150000]],
  ],
  [
    "pm-1010",
    "2026-10-10T16:10:00+07:00",
    "an",
    "VNPAY",
    [
      ["Công thức máu", 250000],
      ["Phí khám", 200000],
    ],
  ],
  [
    "pm-1009",
    "2026-10-05T11:00:00+07:00",
    "lan",
    "VNPAY",
    [["Điện tâm đồ", 180000]],
  ],
  [
    "pm-1008",
    "2026-09-30T09:20:00+07:00",
    "an",
    "CASH",
    [["Phí khám", 200000]],
  ],
  [
    "pm-1007",
    "2026-09-28T15:40:00+07:00",
    "huy",
    "VNPAY",
    [
      ["Phí khám nhi", 150000],
      ["Xét nghiệm nước tiểu", 120000],
    ],
  ],
  [
    "pm-1006",
    "2026-09-22T10:00:00+07:00",
    "lan",
    "CASH",
    [["Phí khám", 200000]],
  ],
  [
    "pm-1005",
    "2026-09-18T13:15:00+07:00",
    "an",
    "VNPAY",
    [["MRI cột sống thắt lưng", 1200000]],
  ],
  [
    "pm-1004",
    "2026-09-10T08:30:00+07:00",
    "an",
    "CASH",
    [
      ["Phí khám", 200000],
      ["Chụp X-quang ngực", 180000],
    ],
  ],
  [
    "pm-1003",
    "2026-09-02T17:05:00+07:00",
    "lan",
    "VNPAY",
    [["Đo loãng xương", 350000]],
  ],
  [
    "pm-1002",
    "2026-08-29T09:50:00+07:00",
    "huy",
    "CASH",
    [["Phí khám nhi", 150000]],
  ],
  [
    "pm-1001",
    "2026-08-26T10:40:00+07:00",
    "an",
    "VNPAY",
    [["Phí khám", 200000]],
  ],
];
