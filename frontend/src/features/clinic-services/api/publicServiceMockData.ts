import type {
  PublicBookingDetails,
  PublicService,
} from "../types/clinicService";

// Dữ liệu giả danh mục công khai, theo thứ tự hiển thị: 12 dịch vụ đang cung cấp (4 Khám,
// 4 Chẩn đoán hình ảnh, 4 Xét nghiệm) và 1 dịch vụ đã ngừng (không hiện, trả 404).
// Giá và mô tả là số mẫu, không phải bảng giá thật.

export type PublicServiceRecord = PublicService & {
  status: "ACTIVE" | "DISCONTINUED";
};

const exam = (slotMinutes: number, patientNote: string | null = null): PublicBookingDetails => ({
  slotMinutes,
  patientNote,
});

const record = (
  id: string,
  fields: Omit<PublicServiceRecord, "id" | "status">,
  status: PublicServiceRecord["status"] = "ACTIVE",
): PublicServiceRecord => ({ id, ...fields, status });

export const PUBLIC_SERVICE_SEED: PublicServiceRecord[] = [
  record("svc-consultation", {
    name: "Khám chấn thương chỉnh hình",
    type: "EXAMINATION",
    price: 250000,
    description: "Bác sĩ chuyên khoa khám, chẩn đoán và lên hướng điều trị.",
    bookingDetails: exam(30, "Mang theo phim chụp cũ và đơn thuốc đang dùng, nếu có."),
  }),
  record("svc-arthroscopy", {
    name: "Nội soi khớp gối",
    type: "EXAMINATION",
    price: 9800000,
    description: "Xử lý rách sụn chêm, tái tạo dây chằng qua vết mổ nhỏ.",
    bookingDetails: exam(30, "Bác sĩ khám trước để quyết định có cần nội soi hay không."),
  }),
  record("svc-physiotherapy", {
    name: "Vật lý trị liệu phục hồi",
    type: "EXAMINATION",
    price: 320000,
    description: "Bài tập theo phác đồ, ghi lại tiến triển sau mỗi buổi.",
    bookingDetails: exam(45),
  }),
  record("svc-injection", {
    name: "Tiêm khớp giảm đau",
    type: "EXAMINATION",
    price: 450000,
    description: "Tiêm dưới hướng dẫn siêu âm cho thoái hóa và viêm khớp.",
    bookingDetails: exam(30, "Báo bác sĩ nếu đang dùng thuốc chống đông máu."),
  }),
  record("svc-xray", {
    name: "Chụp X-quang kỹ thuật số",
    type: "IMAGING",
    price: 180000,
    description: "Có phim sau khoảng 15 phút, bác sĩ đọc ngay trong lượt khám.",
    bookingDetails: null,
  }),
  record("svc-mri", {
    name: "Chụp MRI khớp",
    type: "IMAGING",
    price: 2150000,
    description: "Đánh giá sụn, dây chằng và tủy xương khi X-quang chưa đủ.",
    bookingDetails: null,
  }),
  record("svc-ultrasound", {
    name: "Siêu âm khớp",
    type: "IMAGING",
    price: 200000,
    description: "Xem gân, bao khớp và dịch khớp ngay tại phòng chẩn đoán.",
    bookingDetails: null,
  }),
  record("svc-ct", {
    name: "Chụp CT cột sống",
    type: "IMAGING",
    price: 1200000,
    description: "Hình cắt lớp để xem chi tiết xương khi cần đánh giá đốt sống.",
    bookingDetails: null,
  }),
  record("svc-blood-test", {
    name: "Xét nghiệm máu tổng quát",
    type: "LABORATORY",
    price: 120000,
    description: "Công thức máu và các chỉ số cơ bản để bác sĩ đánh giá tổng quát.",
    bookingDetails: null,
  }),
  record("svc-crp", {
    name: "Xét nghiệm CRP định lượng",
    type: "LABORATORY",
    price: 150000,
    description: "Đo chỉ số viêm, hỗ trợ theo dõi sau chấn thương hoặc nhiễm trùng.",
    bookingDetails: null,
  }),
  record("svc-uric-acid", {
    name: "Xét nghiệm axit uric",
    type: "LABORATORY",
    price: 90000,
    description: "Hỗ trợ đánh giá nguy cơ gút khi đau khớp ngón chân.",
    bookingDetails: null,
  }),
  record("svc-vitamin-d", {
    name: "Xét nghiệm vitamin D",
    type: "LABORATORY",
    price: 350000,
    description: "Đánh giá tình trạng xương khi nghi loãng xương hoặc thiếu vitamin D.",
    bookingDetails: null,
  }),
  record(
    "svc-film-xray-retired",
    {
      name: "Chụp X-quang bằng phim",
      type: "IMAGING",
      price: 150000,
      description: null,
      bookingDetails: null,
    },
    "DISCONTINUED",
  ),
];
