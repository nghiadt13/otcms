// Dịch vụ đang hoạt động trong danh mục công khai (BR-65): tên, mô tả ngắn và
// giá công khai hiện hành. `priceVnd` null nghĩa là giá tùy chỉ định của bác sĩ.
export type ClinicService = {
  id: string;
  name: string;
  summary: string;
  priceVnd: number | null;
  // Đơn vị tính giá, ví dụ "buổi", "lần"; bỏ trống là giá trọn gói một lượt.
  priceUnit?: string;
  featured: boolean;
};
