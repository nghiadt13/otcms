import { cache } from "react";
import type { WebsiteContent } from "../types/websiteContent";

// LỚP API GIẢ: chưa có backend nên nội dung website là dữ liệu mẫu (tên, địa chỉ,
// hotline đều không có thật). Khi có endpoint nội dung công khai, giữ chữ ký hàm
// và thay phần thân bằng apiRequest (`@/lib/api/client`); component không cần đổi.
const publishedContent: WebsiteContent = {
  contact: {
    clinicName: "Phòng khám Chấn thương Chỉnh hình OTCMS",
    address: "123 Đường Mẫu, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
    hotline: "1900 0000",
    email: "lienhe@otcms.example",
    openingHours: ["Thứ 2 – Thứ 6: 7:30 – 17:00", "Thứ 7: 7:30 – 11:30"],
  },
  hero: {
    title: "Chăm sóc bạn. Kết nối mọi bước khám.",
    description:
      "Đặt lịch với bác sĩ chỉnh hình, theo dõi từng bước của lượt khám và xem báo cáo, ảnh chẩn đoán trực tuyến ngay khi bác sĩ phát hành.",
    highlights: [
      "Bác sĩ chuyên khoa chấn thương chỉnh hình",
      "Chụp X-quang và đọc kết quả tại chỗ",
      "Hồ sơ khám lưu trữ, xem lại mọi lúc",
    ],
  },
};

// `cache`: layout (header/footer) và trang chủ (hero) cùng gọi trong một lần
// render nhưng chỉ tải một lần khi đã nối backend.
export const getPublishedWebsiteContent = cache(
  async (): Promise<WebsiteContent> => structuredClone(publishedContent),
);
