import type { ServiceCategory } from "@/lib/domain/clinicCategories";

// Dịch vụ đang cung cấp trong danh mục công khai (BR-60): tên, loại, giá hiện hành và
// thông tin đặt lịch. Không có trạng thái hay quyền thao tác; đó là kiểu của màn Admin
// (`serviceCatalog.ts`).
export type PublicService = {
  id: string;
  name: string;
  type: ServiceCategory;
  /** VND nguyên, giá công khai hiện hành. */
  price: number;
  description: string | null;
  /** null khi dịch vụ không có thông tin đặt lịch riêng. */
  bookingDetails: PublicBookingDetails | null;
};

export type PublicBookingDetails = {
  /** Số phút mỗi lượt; chỉ dịch vụ Khám có khung giờ. */
  slotMinutes: number | null;
  /** Lời dặn trước khi đến; null nếu không có. */
  patientNote: string | null;
};

/** Tiêu chí tìm kiếm danh mục công khai: `q` là tên (bỏ dấu khi so khớp), `type` là loại. */
export type PublicServiceQuery = {
  q?: string;
  type?: ServiceCategory;
};
