import {
  CATEGORY_LABELS,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import type {
  ReportColumn,
  ReportQuery,
  ReportResult,
  ReportRow,
  ReportSection,
  ReportSubjectCode,
  ReportSubjectOption,
} from "../types/report";
import {
  buildPaymentSections,
  makeSection,
  pick,
  listDays,
  col,
} from "./reportMockPayments";

/** Chủ đề bị ẩn khỏi danh sách và trả 403 khi tạo. Đặt `[]` để xem đủ 7 chủ đề. */
export const MOCK_EXCLUDED_SUBJECTS: ReportSubjectCode[] = ["PATIENT_FEEDBACK"];
/** Ngày `to` này luôn trả 500 để kiểm toast lỗi và việc giữ kết quả cũ. */
export const MOCK_FAILING_TO_DATE = "2026-10-13";
/** Kỳ có `from` trước mốc này không có hoạt động. */
export const MOCK_EMPTY_BEFORE = "2026-01-01";

export const MOCK_SUBJECTS: ReportSubjectOption[] = [
  { code: "APPOINTMENTS", label: "Lịch hẹn", breakdownOptions: [] },
  { code: "VISITS", label: "Lượt khám", breakdownOptions: [] },
  { code: "CLINICAL_ORDERS", label: "Cận lâm sàng", breakdownOptions: [] },
  { code: "SERVICES", label: "Dịch vụ", breakdownOptions: [] },
  {
    code: "PAYMENTS",
    label: "Thanh toán",
    breakdownOptions: ["DATE", "RECEPTIONIST", "PAYMENT_METHOD"],
  },
  {
    code: "PENDING_AND_CANCELLED",
    label: "Chỉ định chờ và đã hủy",
    breakdownOptions: [],
  },
  {
    code: "PATIENT_FEEDBACK",
    label: "Phản hồi bệnh nhân",
    breakdownOptions: [],
  },
];

const DOCTORS = ["BS. Phạm Quang Huy", "BS. Đỗ Thanh Mai", "BS. Vũ Đức Anh"];
const SERVICE_ROWS: [string, ServiceCategory][] = [
  ["Khám cơ xương khớp", "EXAMINATION"],
  ["Tái khám sau phẫu thuật", "EXAMINATION"],
  ["Khám chấn thương thể thao", "EXAMINATION"],
  ["Khám cột sống", "EXAMINATION"],
  ["X-quang cột sống thắt lưng", "IMAGING"],
  ["X-quang khớp gối", "IMAGING"],
  ["Chụp cộng hưởng từ khớp vai", "IMAGING"],
  ["Siêu âm khớp", "IMAGING"],
  ["Xét nghiệm máu tổng quát", "LABORATORY"],
  ["Xét nghiệm acid uric", "LABORATORY"],
  ["Xét nghiệm CRP", "LABORATORY"],
  ["Xét nghiệm mật độ xương", "LABORATORY"],
];

function labelRows(labels: string[], salt: string): ReportRow[] {
  return labels.map((label) => ({ label, n: pick(`${salt}${label}`, 3, 60) }));
}

function countSection(
  title: string,
  head: string,
  countLabel: string,
  rows: ReportRow[],
) {
  const columns: ReportColumn[] = [
    col("label", head, "text"),
    col("n", countLabel, "count"),
  ];
  return makeSection(title, columns, rows);
}

function buildFeedback(): ReportSection[] {
  const stars = [5, 4, 3, 2, 1].map((s) => ({
    s,
    star: `${s} sao`,
    n: pick(`pf${s}`, s >= 4 ? 8 : 0, s >= 4 ? 40 : 9),
  }));
  const total = stars.reduce((a, r) => a + r.n, 0);
  const avg = total ? stars.reduce((a, r) => a + r.n * r.s, 0) / total : null;
  return [
    makeSection(
      "Tổng hợp",
      [
        col("avg", "Điểm trung bình", "score"),
        col("n", "Số đánh giá", "count"),
      ],
      [{ avg, n: total }],
      false,
    ),
    makeSection(
      "Theo số sao",
      [col("star", "Số sao", "text"), col("n", "Số đánh giá", "count")],
      stars.map(({ star, n }) => ({ star, n })),
    ),
  ];
}

function buildSections(q: ReportQuery): ReportSection[] {
  switch (q.subject) {
    case "APPOINTMENTS":
      return [
        countSection(
          "Theo trạng thái",
          "Trạng thái",
          "Số lịch hẹn",
          labelRows(["Đã đặt", "Đã check-in", "Đã hủy", "Không đến"], "as"),
        ),
        countSection(
          "Theo bác sĩ",
          "Bác sĩ",
          "Số lịch hẹn",
          labelRows(DOCTORS, "ad"),
        ),
        makeSection(
          "Theo ngày",
          [col("label", "Ngày", "date"), col("n", "Số lịch hẹn", "count")],
          listDays(q.from, q.to).map((d) => ({
            label: d,
            n: pick(`ag${d}`, 0, 25),
          })),
        ),
      ];
    case "VISITS":
      return [
        countSection(
          "Theo trạng thái",
          "Trạng thái",
          "Số lượt",
          labelRows(["Đang chờ", "Đang khám", "Hoàn tất", "Đã hủy"], "vs"),
        ),
        countSection(
          "Theo loại",
          "Loại",
          "Số lượt",
          labelRows(["Khám lần đầu", "Tái khám"], "vt"),
        ),
      ];
    case "CLINICAL_ORDERS":
      return [
        countSection(
          "Theo loại",
          "Loại chỉ định",
          "Số đã thực hiện",
          labelRows(["Chẩn đoán hình ảnh", "Xét nghiệm"], "co"),
        ),
      ];
    case "SERVICES":
      return [
        makeSection(
          "Theo dịch vụ",
          [
            col("name", "Dịch vụ", "text"),
            col("category", "Loại", "text"),
            col("n", "Số lần dùng", "count"),
          ],
          SERVICE_ROWS.map(([name, cat]) => ({
            name,
            category: CATEGORY_LABELS[cat],
            n: pick(`sv${name}`, 2, 90),
          })),
        ),
      ];
    case "PAYMENTS":
      return buildPaymentSections(q);
    case "PENDING_AND_CANCELLED":
      return [
        countSection(
          "Theo trạng thái",
          "Trạng thái",
          "Số chỉ định",
          labelRows(["Chờ thanh toán", "Đã hủy"], "pc"),
        ),
      ];
    case "PATIENT_FEEDBACK":
      return buildFeedback();
  }
}

/** Dựng kết quả báo cáo giả. Kỳ có `from` < MOCK_EMPTY_BEFORE → không có hoạt động. */
export function buildMockReport(q: ReportQuery): ReportResult {
  const subject = MOCK_SUBJECTS.find((s) => s.code === q.subject);
  const query: ReportQuery = { subject: q.subject, from: q.from, to: q.to };
  // breakdown chỉ giữ lại khi chủ đề hỗ trợ.
  if (q.breakdown && subject?.breakdownOptions.includes(q.breakdown))
    query.breakdown = q.breakdown;
  const sections = buildSections(query);
  const hasData = query.from >= MOCK_EMPTY_BEFORE;
  return {
    query,
    hasData,
    generatedAt: new Date().toISOString(),
    sections: hasData
      ? sections
      : sections.map((s) => ({ ...s, rows: [], totalRow: null })),
    allowedActions: ["export"],
  };
}
