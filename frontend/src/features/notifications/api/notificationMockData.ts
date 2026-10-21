import type {
  NotificationItem,
  NotificationRelatedRecord,
} from "../types/notification";

// Id đặc biệt: thông báo của người khác, API trả 404 MSG-50 (BR-01).
export const FOREIGN_NOTIFICATION_ID = "n-foreign";

type Template = {
  title: string;
  content: string;
  /** Có bản ghi liên quan hay không. */
  related: boolean;
};

const TEMPLATES: Template[] = [
  {
    title: "Lịch hẹn mới cần xác nhận",
    content:
      "Có lịch khám mới lúc 09:00 ngày mai. Kiểm tra thông tin và chuyển cho bác sĩ phụ trách.",
    related: true,
  },
  {
    title: "Lịch hẹn cần chuyển bác sĩ",
    content:
      "Bác sĩ được chọn không còn ca làm việc vào thời điểm này. Chuyển lịch hẹn sang bác sĩ khác để bệnh nhân vẫn được khám đúng giờ.",
    related: true,
  },
  {
    title: "Tài khoản nhân viên mới được tạo",
    content:
      "Tài khoản lễ tân mới đã được tạo với mật khẩu khởi tạo. Nhân viên sẽ phải đổi mật khẩu ở lần đăng nhập đầu tiên.",
    related: false,
  },
  {
    title: "Đánh giá mới chờ duyệt",
    content: "Có đánh giá của bệnh nhân đang chờ duyệt hiển thị trên trang web.",
    related: false,
  },
  {
    title: "Lịch hẹn bị hủy",
    content:
      "Bệnh nhân đã hủy lịch khám. Khung giờ này đã được trả về trạng thái còn trống.",
    related: true,
  },
  {
    title: "Phòng chẩn đoán hình ảnh tạm ngưng",
    content:
      "Phòng chẩn đoán hình ảnh đã chuyển sang tạm ngưng. Kiểm tra các ca làm việc còn phân công vào phòng này.",
    related: false,
  },
  {
    title: "Ca làm việc trùng giờ",
    content:
      "Một ca làm việc mới bị trùng với ca đã có của cùng nhân viên. Điều chỉnh giờ hoặc phân công lại.",
    related: false,
  },
  {
    title: "Lịch hẹn đã được cập nhật",
    content: "Lễ tân vừa đổi giờ của một lịch hẹn. Xem lại để bảo đảm không trùng ca.",
    related: true,
  },
];

const BASE_TIME = Date.parse("2026-10-20T08:45:00+07:00");
const STEP_MS = 5 * 60 * 60 * 1000;

/** Chuỗi ISO có offset +07:00 từ mốc thời gian (ms). */
function toVietnamIso(ms: number): string {
  const shifted = new Date(ms + 7 * 60 * 60 * 1000);
  return `${shifted.toISOString().slice(0, 19)}+07:00`;
}

function buildMockNotifications(count: number): NotificationItem[] {
  return Array.from({ length: count }, (_, index) => {
    const template = TEMPLATES[index % TEMPLATES.length];
    // 6 mới nhất và mỗi mục thứ 3 là chưa đọc (12/23), để badge vượt 9.
    const read = !(index < 6 || index % 3 === 0);
    let relatedRecord: NotificationRelatedRecord | null = null;
    if (template.related) {
      relatedRecord = {
        kind: "APPOINTMENT",
        id: `0c9d8e7f-6a5b-4c3d-9e1f-2b3a4c5d${String(index).padStart(4, "0")}`,
        // Chỉ thông báo đã đọc mới có bản ghi đã mất (vị trí lẻ).
        available: !(read && index % 2 === 1),
      };
    }
    return {
      id: `n-${String(index + 1).padStart(3, "0")}`,
      title: template.title,
      content: template.content,
      createdAt: toVietnamIso(BASE_TIME - index * STEP_MS),
      read,
      relatedRecord,
      allowedActions: read ? [] : ["markRead"],
    };
  });
}

/** 23 thông báo, mới nhất trước: 2 trang khi size = 20; đủ chưa đọc/đã đọc, có/không liên kết. */
export const MOCK_NOTIFICATIONS: NotificationItem[] = buildMockNotifications(23);
