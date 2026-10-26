import type { ExaminationResult } from "../types/examinationResult";
import {
  AN,
  AN_ADMIN,
  LAN,
  LAN_ADMIN,
  SHEET_SAMPLE_URL,
} from "./examinationResultMockPeople";

// Lượt khám mẫu: một người có hai lượt (trước/sau), một người có lượt Chưa hoàn tất.

/** Lượt khám mẫu kèm tên dịch vụ để dựng dòng danh sách. */
export interface MockVisit {
  serviceName: string;
  result: ExaminationResult;
}

export const MOCK_VISITS: MockVisit[] = [
  {
    serviceName: "Khám tổng quát",
    result: {
      visitId: "vis-an-prev",
      visitDate: "2026-06-12",
      status: "COMPLETED",
      patient: AN,
      previousVisitId: null,
      nextVisitId: "vis-an-done",
      visibleBlocks: ["EXAMINATION", "ADMINISTRATIVE"],
      examination: {
        reason: "Đau vai gáy kéo dài hai tuần",
        symptoms: "Đau âm ỉ, tăng khi ngồi làm việc lâu",
        findings: "Cơ cạnh cột sống cổ co cứng nhẹ; vận động cổ còn đủ",
        primaryDiagnoses: [{ code: "M54.2", title: "Đau cổ" }],
        secondaryDiagnoses: [],
        treatmentDirection: "Tập giãn cơ, chỉnh tư thế ngồi",
        advice: "Đứng dậy vận động mỗi 45 phút khi làm việc trên máy tính.",
        followUpDate: null,
      },
      administrative: AN_ADMIN,
    },
  },
  {
    serviceName: "Khám chỉnh hình tổng quát",
    result: {
      visitId: "vis-an-done",
      visitDate: "2026-10-23",
      status: "COMPLETED",
      patient: AN,
      previousVisitId: "vis-an-prev",
      nextVisitId: null,
      visibleBlocks: [
        "EXAMINATION",
        "IMAGING",
        "LABORATORY",
        "PRESCRIPTIONS",
        "ADMINISTRATIVE",
        "RESULT_SHEET",
      ],
      examination: {
        reason: "Đau khớp gối phải ba tháng",
        symptoms:
          "Đau khi lên xuống cầu thang, cứng khớp buổi sáng khoảng 15 phút",
        findings:
          "Khớp gối phải tràn dịch nhẹ, gấp gối 120°, đau khi ép mặt trong",
        primaryDiagnoses: [
          { code: "M17.1", title: "Thoái hóa khớp gối nguyên phát" },
        ],
        secondaryDiagnoses: [{ code: "E79.0", title: "Tăng acid uric máu" }],
        treatmentDirection: "Dùng thuốc giảm đau, tập vật lý trị liệu",
        advice: "Giảm cân; tránh leo núi và chạy bộ trong hai tuần.",
        followUpDate: "2026-11-06",
      },
      imaging: [
        {
          studyId: "std-an-xray-1",
          serviceName: "X-quang khớp gối 2 tư thế",
          finding: "Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày.",
        },
      ],
      laboratory: [
        {
          serviceName: "Xét nghiệm máu",
          lines: [
            {
              indicator: "Acid uric",
              value: "512",
              unit: "µmol/L",
              referenceRange: "202–416 (nam)",
              flag: "HIGH",
            },
            {
              indicator: "Hemoglobin",
              value: "118",
              unit: "g/L",
              referenceRange: "130–170 (nam)",
              flag: "LOW",
            },
            {
              indicator: "Đường huyết lúc đói",
              value: "5,1",
              unit: "mmol/L",
              referenceRange: "3,9–6,1",
              flag: null,
            },
          ],
        },
      ],
      prescriptions: [
        {
          id: "pres-an-1",
          issuedAt: "2026-10-23T10:40:00+07:00",
          itemCount: 3,
        },
      ],
      administrative: AN_ADMIN,
      resultSheets: [
        {
          version: 2,
          status: "CURRENT",
          issuedAt: "2026-10-24T08:15:00+07:00",
          fileUrl: SHEET_SAMPLE_URL,
        },
        {
          version: 1,
          status: "REPLACED",
          issuedAt: "2026-10-23T16:00:00+07:00",
          fileUrl: SHEET_SAMPLE_URL,
        },
      ],
    },
  },
  {
    serviceName: "Khám tổng quát",
    result: {
      visitId: "vis-lan-prev",
      visitDate: "2026-07-03",
      status: "COMPLETED",
      patient: LAN,
      previousVisitId: null,
      nextVisitId: "vis-lan-incomplete",
      visibleBlocks: ["EXAMINATION", "PRESCRIPTIONS", "ADMINISTRATIVE"],
      examination: {
        reason: "Đau thắt lưng khi đứng lâu",
        symptoms: "Đau vùng thắt lưng, lan nhẹ xuống mông",
        findings:
          "Vận động cột sống thắt lưng hạn chế nhẹ, không có dấu thần kinh",
        primaryDiagnoses: [{ code: "M54.5", title: "Đau thắt lưng" }],
        secondaryDiagnoses: [],
        treatmentDirection: "Dùng thuốc giảm đau, chườm ấm",
        advice: "Tránh mang vác vật nặng trong một tuần.",
        followUpDate: null,
      },
      prescriptions: [
        {
          id: "pres-lan-1",
          issuedAt: "2026-07-03T09:30:00+07:00",
          itemCount: 2,
        },
      ],
      administrative: LAN_ADMIN,
    },
  },
  {
    serviceName: "Khám chỉnh hình",
    result: {
      visitId: "vis-lan-incomplete",
      visitDate: "2026-09-15",
      status: "INCOMPLETE",
      patient: LAN,
      previousVisitId: "vis-lan-prev",
      nextVisitId: null,
      visibleBlocks: ["IMAGING", "LABORATORY", "ADMINISTRATIVE"],
      imaging: [
        {
          studyId: "std-lan-us-1",
          serviceName: "Siêu âm khớp gối phải",
          finding: null,
        },
      ],
      laboratory: [
        {
          serviceName: "Xét nghiệm máu",
          lines: [
            {
              indicator: "Creatinin",
              value: "68",
              unit: "µmol/L",
              referenceRange: "44–80 (nữ)",
              flag: null,
            },
            {
              indicator: "Đường huyết lúc đói",
              value: "4,8",
              unit: "mmol/L",
              referenceRange: "3,9–6,1",
              flag: null,
            },
          ],
        },
      ],
      administrative: LAN_ADMIN,
    },
  },
];
