import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { addDays, formatDate, todayIsoDate } from "@/lib/format/formatDate";
import type {
  NotificationItem,
  NotificationRelatedRecord,
} from "../types/notification";

// DỮ LIỆU GIẢ thông báo khu bệnh nhân (tài khoản của Nguyễn Văn An; hồ sơ đã gắn: An, Lan, Huy).
// Nội dung là câu MSG của bảng R3 đã điền tham số (MSG-76, 83, 84, 85, 87, 89). R3 chưa có MSG riêng
// cho thông báo thanh toán nên hai mục PAYMENT viết tạm (xem câu hỏi mở trong báo cáo P08).
// Mã bản ghi khớp mock lịch hẹn, lượt khám, kết quả và thanh toán của phase 04–07.

const today = todayIsoDate();
const dmy = (offset: number) => formatDate(addDays(today, offset));

function item(
  id: string,
  title: string,
  content: string,
  createdAt: string,
  read: boolean,
  relatedRecord: NotificationRelatedRecord | null,
): NotificationItem {
  return {
    id,
    title,
    content,
    createdAt,
    read,
    relatedRecord,
    allowedActions: read ? [] : ["markRead"],
  };
}

/** 8 thông báo đủ bốn loại bản ghi; 3 chưa đọc; 1 bản ghi không còn khả dụng. Mới nhất trước. */
export const MOCK_PATIENT_NOTIFICATIONS: NotificationItem[] = [
  item(
    "np-01",
    "Lịch khám của Nguyễn Gia Huy cần xử lý",
    `Bác sĩ của lịch hẹn ${dmy(3)} 09:00 không thể khám và chưa có bác sĩ thay thế. Bạn có thể hủy lịch hẹn trong mục Lịch hẹn. Phí khám đã thanh toán trực tuyến không được hoàn lại.`,
    "2026-10-24T08:10:00+07:00",
    false,
    { kind: "APPOINTMENT", id: "apt-2101", available: true },
  ),
  item(
    "np-02",
    "Đã đủ kết quả cận lâm sàng",
    "Đã có đủ kết quả cận lâm sàng của Nguyễn Văn An. Vui lòng đến phòng Khám 2 để bác sĩ đọc kết quả.",
    "2026-10-23T18:20:00+07:00",
    false,
    { kind: "VISIT", id: "vis-an-today", available: true },
  ),
  item(
    "np-03",
    "Kết quả khám đã được công bố",
    `Kết quả khám ngày ${dmy(-14)} của Nguyễn Văn An đã được công bố. Vui lòng đăng nhập để xem.`,
    "2026-10-23T16:00:00+07:00",
    false,
    { kind: "VISIT_RESULT", id: "vis-an-done", available: true },
  ),
  item(
    "np-04",
    "Đã ghi nhận thanh toán",
    `Đã ghi nhận thanh toán ${formatCurrencyVnd(200000)} cho X-quang khớp gối của Nguyễn Văn An.`,
    "2026-10-23T10:25:00+07:00",
    true,
    { kind: "PAYMENT", id: "pm-1029", available: true },
  ),
  item(
    "np-05",
    "Phiếu kết quả khám có phiên bản mới",
    `Phiếu kết quả khám ngày ${dmy(-14)} của Nguyễn Văn An đã có phiên bản mới. Vui lòng xem lại.`,
    "2026-10-22T10:30:00+07:00",
    true,
    { kind: "VISIT_RESULT", id: "vis-an-done", available: true },
  ),
  item(
    "np-06",
    "Nhắc lịch khám của Nguyễn Văn An",
    `Nhắc lịch khám: Nguyễn Văn An có lịch khám chuyên khoa vào 10:00 ngày ${dmy(5)}.`,
    "2026-10-21T08:00:00+07:00",
    true,
    { kind: "APPOINTMENT", id: "apt-race", available: true },
  ),
  item(
    "np-07",
    "Lượt khám của Nguyễn Thị Lan đã đóng",
    `Lượt khám ngày ${dmy(-16)} của Nguyễn Thị Lan đã đóng ở trạng thái chưa hoàn tất. Ảnh và kết quả xét nghiệm đã được công bố; các chỉ định chưa có kết quả đã bị hủy, không hoàn tiền.`,
    "2026-10-20T09:00:00+07:00",
    true,
    { kind: "VISIT", id: "vis-lan-incomplete", available: true },
  ),
  item(
    "np-08",
    "Khoản thanh toán của Nguyễn Thị Lan",
    `Đã ghi nhận thanh toán ${formatCurrencyVnd(180000)} cho Điện tâm đồ của Nguyễn Thị Lan.`,
    "2026-10-18T14:00:00+07:00",
    true,
    // Hồ sơ đã bỏ gắn: bản ghi không còn truy cập được (BR-19), ẩn nút Mở.
    { kind: "PAYMENT", id: "pm-1009", available: false },
  ),
];
