// Thông báo cá nhân (UC64). Bản nháp contract: đối chiếu lại khi backend chốt DTO.

export type Notification = {
  id: string;
  title: string;
  body: string;
  /** ISO 8601. */
  createdAt: string;
  read: boolean;
  /** Trang liên quan; bấm vào thông báo sẽ chuyển tới đây. */
  href?: string;
};
