// Kiểu dữ liệu phòng khám (UC65–UC68). Đây là bản nháp contract: khi backend
// chốt DTO thì đối chiếu lại các trường ở đây.

export type RoomType = "examination" | "imaging" | "procedure";
export type RoomStatus = "available" | "unavailable";

export type Room = {
  id: string;
  name: string;
  type: RoomType;
  description: string;
  supportedServices: string[];
  status: RoomStatus;
};

/** Dữ liệu người dùng nhập khi thêm/sửa phòng. Trạng thái đổi riêng (UC68). */
export type RoomInput = Omit<Room, "id" | "status">;

export const ROOM_TYPE_OPTIONS: { value: RoomType; label: string }[] = [
  { value: "examination", label: "Phòng khám" },
  { value: "imaging", label: "Chẩn đoán hình ảnh" },
  { value: "procedure", label: "Thủ thuật" },
];

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  available: "Sẵn sàng",
  unavailable: "Tạm ngưng",
};

// Tạm thời cố định; sau này lấy từ danh mục dịch vụ (UC69–UC72).
export const ROOM_SERVICE_OPTIONS = [
  "Khám chỉnh hình",
  "Tái khám",
  "X-quang",
  "MRI",
  "CT",
  "Siêu âm",
  "Bó bột",
  "Tiêm khớp",
];
