import type {
  LabIndicator,
  ServiceCatalogDetail,
  ServiceModality,
} from "../types/serviceCatalog";

// Dữ liệu giả cho màn Admin "Dịch vụ và giá": 26 bản ghi (2 trang), 3 bản ghi đã ngừng
// (`svc-disc-01..03`), một dịch vụ giá 0 (`svc-04`). BE thật dùng UUID.

type Seed = Pick<ServiceCatalogDetail, "id" | "name" | "type" | "price"> &
  Partial<ServiceCatalogDetail>;

function make(seed: Seed): ServiceCatalogDetail {
  const discontinued = seed.status === "DISCONTINUED";
  return {
    modality: null,
    status: "ACTIVE",
    description: null,
    bookingDetails: { slotMinutes: null, patientNote: null },
    indicators: [],
    ...seed,
    allowedActions: discontinued ? [] : ["edit", "discontinue"],
  };
}

const sid = (n: number) => `svc-${String(n).padStart(2, "0")}`;

const examination = (
  n: number,
  name: string,
  price: number,
  extra: Partial<Seed> = {},
) =>
  make({
    id: sid(n),
    name,
    type: "EXAMINATION",
    price,
    bookingDetails: { slotMinutes: 30, patientNote: null },
    ...extra,
  });

const imaging = (
  n: number,
  name: string,
  modality: ServiceModality,
  price: number,
  extra: Partial<Seed> = {},
) =>
  make({ id: sid(n), name, type: "IMAGING", modality, price, ...extra });

const ind = (
  name: string,
  unit: string,
  m: [number, number],
  f: [number, number],
): LabIndicator => ({
  name,
  unit,
  refMale: { min: m[0], max: m[1] },
  refFemale: { min: f[0], max: f[1] },
});

const lab = (
  n: number,
  name: string,
  price: number,
  indicators: LabIndicator[],
  extra: Partial<Seed> = {},
) =>
  make({ id: sid(n), name, type: "LABORATORY", price, indicators, ...extra });

export function buildServiceCatalogSeed(): ServiceCatalogDetail[] {
  return [
    examination(1, "Khám nội tổng quát", 200000, {
      description: "Khám tổng quát với bác sĩ chuyên khoa.",
      bookingDetails: {
        slotMinutes: 30,
        patientNote: "Mang theo kết quả khám cũ nếu có.",
      },
    }),
    examination(2, "Khám xương khớp", 250000),
    examination(3, "Khám cột sống", 280000, {
      bookingDetails: { slotMinutes: 45, patientNote: null },
    }),
    examination(4, "Tái khám sau phẫu thuật", 0, {
      description: "Miễn phí trong 30 ngày đầu.",
    }),
    examination(5, "Khám chấn thương thể thao", 300000),
    examination(6, "Khám nhi chỉnh hình", 260000, {
      id: "svc-disc-02",
      status: "DISCONTINUED",
    }),
    imaging(7, "Chụp X-quang cột sống thắt lưng", "XRAY", 180000),
    imaging(8, "Chụp X-quang khớp gối", "XRAY", 150000),
    imaging(9, "Siêu âm khớp vai", "ULTRASOUND", 220000),
    imaging(10, "Siêu âm phần mềm", "ULTRASOUND", 200000),
    imaging(11, "Chụp CT sọ não", "CT", 850000, {
      description: "Không cần nhịn ăn.",
    }),
    imaging(12, "Chụp CT cột sống cổ", "CT", 950000),
    imaging(13, "Chụp MRI khớp gối", "MRI", 1450000, {
      bookingDetails: {
        slotMinutes: null,
        patientNote: "Tháo toàn bộ kim loại trước khi chụp.",
      },
    }),
    imaging(14, "Chụp MRI cột sống thắt lưng", "MRI", 1650000, {
      id: "svc-disc-03",
      status: "DISCONTINUED",
    }),
    lab(
      15,
      "Công thức máu",
      120000,
      [
        ind("Hemoglobin", "g/dL", [13, 17.5], [12, 15.5]),
        ind("Bạch cầu", "G/L", [4, 10], [4, 10]),
        ind("Tiểu cầu", "G/L", [150, 400], [150, 400]),
      ],
      {
        description: "Đếm tế bào máu ngoại vi.",
        bookingDetails: {
          slotMinutes: null,
          patientNote: "Nhịn ăn 8 giờ trước khi lấy mẫu.",
        },
      },
    ),
    lab(16, "Xét nghiệm đường huyết", 45000, [
      ind("Glucose lúc đói", "mmol/L", [3.9, 5.6], [3.9, 5.6]),
    ]),
    lab(17, "Xét nghiệm mỡ máu", 160000, [
      ind("Cholesterol toàn phần", "mmol/L", [3.6, 5.2], [3.6, 5.2]),
      ind("Triglyceride", "mmol/L", [0.46, 2.26], [0.46, 2.26]),
      ind("HDL-C", "mmol/L", [1, 2.1], [1.2, 2.4]),
      ind("LDL-C", "mmol/L", [0, 3.4], [0, 3.4]),
    ]),
    lab(18, "Xét nghiệm chức năng gan", 210000, [
      ind("AST (SGOT)", "U/L", [0, 40], [0, 32]),
      ind("ALT (SGPT)", "U/L", [0, 41], [0, 33]),
      ind("Bilirubin toàn phần", "µmol/L", [3, 17], [3, 17]),
    ]),
    lab(19, "Xét nghiệm chức năng thận", 190000, [
      ind("Creatinine", "µmol/L", [62, 106], [44, 80]),
      ind("Ure", "mmol/L", [2.5, 7.5], [2.5, 7.5]),
    ]),
    lab(20, "Xét nghiệm acid uric", 60000, [
      ind("Acid uric", "µmol/L", [210, 420], [150, 350]),
    ]),
    lab(
      21,
      "Xét nghiệm viêm khớp dạng thấp (RF, Anti-CCP, CRP, ESR, ASLO và các chỉ số liên quan)",
      480000,
      [
        ind("RF", "IU/mL", [0, 14], [0, 14]),
        ind("Anti-CCP", "U/mL", [0, 17], [0, 17]),
        ind("CRP", "mg/L", [0, 5], [0, 5]),
        ind("ESR (tốc độ máu lắng)", "mm/giờ", [0, 15], [0, 20]),
        ind("ASLO", "IU/mL", [0, 200], [0, 200]),
      ],
    ),
    lab(22, "Xét nghiệm canxi, phospho, vitamin D", 340000, [
      ind("Canxi toàn phần", "mmol/L", [2.15, 2.55], [2.15, 2.55]),
      ind("Phospho", "mmol/L", [0.81, 1.45], [0.81, 1.45]),
      ind("Vitamin D (25-OH)", "ng/mL", [30, 100], [30, 100]),
    ]),
    lab(23, "Tổng phân tích nước tiểu", 70000, [
      ind("pH", "-", [5, 8], [5, 8]),
      ind("Tỷ trọng", "g/mL", [1.005, 1.03], [1.005, 1.03]),
    ]),
    lab(24, "Xét nghiệm đông máu cơ bản", 150000, [
      ind("PT", "giây", [11, 13.5], [11, 13.5]),
      ind("APTT", "giây", [25, 35], [25, 35]),
      ind("Fibrinogen", "g/L", [2, 4], [2, 4]),
    ]),
    lab(25, "Xét nghiệm điện giải đồ", 110000, [
      ind("Natri", "mmol/L", [135, 145], [135, 145]),
      ind("Kali", "mmol/L", [3.5, 5.1], [3.5, 5.1]),
      ind("Clo", "mmol/L", [98, 107], [98, 107]),
      ind("Canxi ion hóa", "mmol/L", [1.12, 1.32], [1.12, 1.32]),
      ind("Magie", "mmol/L", [0.7, 1.05], [0.7, 1.05]),
      ind("Bicarbonate", "mmol/L", [22, 29], [22, 29]),
      ind("Phospho vô cơ", "mmol/L", [0.81, 1.45], [0.81, 1.45]),
      ind("Sắt huyết thanh", "µmol/L", [11, 28], [7, 26]),
    ]),
    lab(
      26,
      "Xét nghiệm CRP độ nhạy cao",
      95000,
      [ind("hs-CRP", "mg/L", [0, 3], [0, 3])],
      { id: "svc-disc-01", status: "DISCONTINUED" },
    ),
  ];
}
