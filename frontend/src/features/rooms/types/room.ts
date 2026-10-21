import type { ServiceCategory } from "@/lib/domain/clinicCategories";

// Hình JSON đúng contract `/api/v1/admin/rooms`. Loại phòng dùng chung enum với loại dịch vụ (BR-57).

export type RoomStatus = "AVAILABLE" | "UNAVAILABLE";
export type RoomAction = "edit" | "changeStatus";

export type SupportedService = { id: string; name: string };

export type Room = {
  id: string;
  /** Tên hoặc mã phòng, duy nhất. */
  name: string;
  type: ServiceCategory;
  description: string;
  status: RoomStatus;
  supportedServices: SupportedService[];
  allowedActions: RoomAction[];
};

export type RoomCreateInput = {
  name: string;
  type: ServiceCategory;
  description: string;
  serviceIds: string[];
};
export type RoomUpdateInput = { description: string; serviceIds: string[] };
export type RoomStatusInput = { status: RoomStatus };

export type RoomServiceOption = {
  id: string;
  name: string;
  category: ServiceCategory;
};
export type RoomServiceOptionsResponse = { items: RoomServiceOption[] };

/** Bộ lọc danh sách; giá trị rỗng bị bỏ khi gửi. */
export type RoomListFilters = {
  q: string;
  type: ServiceCategory | "";
  status: RoomStatus | "";
  serviceId: string;
};

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  AVAILABLE: "Sẵn sàng",
  UNAVAILABLE: "Tạm ngưng",
};
