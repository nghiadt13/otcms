import type { ClinicService } from "../types/clinicService";

// LỚP API GIẢ: danh mục dịch vụ và giá là dữ liệu mẫu. Khi có endpoint danh mục
// công khai, giữ chữ ký hàm và thay phần thân bằng apiRequest (`@/lib/api/client`).
const activeServices: ClinicService[] = [
  {
    id: "svc-consultation",
    name: "Khám chấn thương chỉnh hình",
    summary: "Bác sĩ chuyên khoa khám, chẩn đoán và lên hướng điều trị.",
    priceVnd: 250000,
    featured: true,
  },
  {
    id: "svc-xray",
    name: "Chụp X-quang kỹ thuật số",
    summary: "Có phim sau khoảng 15 phút, bác sĩ đọc ngay trong lượt khám.",
    priceVnd: 180000,
    priceUnit: "tư thế",
    featured: true,
  },
  {
    id: "svc-mri",
    name: "Chụp MRI khớp",
    summary: "Đánh giá sụn, dây chằng và tủy xương khi X-quang chưa đủ.",
    priceVnd: 2150000,
    featured: true,
  },
  {
    id: "svc-arthroscopy",
    name: "Nội soi khớp gối",
    summary: "Xử lý rách sụn chêm, tái tạo dây chằng qua vết mổ nhỏ.",
    priceVnd: null,
    featured: true,
  },
  {
    id: "svc-physiotherapy",
    name: "Vật lý trị liệu phục hồi",
    summary: "Bài tập theo phác đồ, ghi lại tiến triển sau mỗi buổi.",
    priceVnd: 320000,
    priceUnit: "buổi",
    featured: true,
  },
  {
    id: "svc-injection",
    name: "Tiêm khớp giảm đau",
    summary: "Tiêm dưới hướng dẫn siêu âm cho thoái hóa và viêm khớp.",
    priceVnd: 450000,
    priceUnit: "lần",
    featured: true,
  },
];

/** Dịch vụ chính hiện trên trang chủ (tối đa `limit` mục). */
export async function listFeaturedServices(
  limit = 6,
): Promise<ClinicService[]> {
  return structuredClone(
    activeServices.filter((service) => service.featured).slice(0, limit),
  );
}
