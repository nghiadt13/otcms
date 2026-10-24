import { cache } from "react";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import type {
  PublicAboutPage,
  PublicHealthArticle,
  WebsiteContent,
} from "../types/websiteContent";
import { websiteContentStore } from "./websiteContentAdminMockData";

// LỚP API GIẢ: chưa có backend nên nội dung website là dữ liệu mẫu (tên, địa chỉ,
// hotline đều không có thật). Khi có endpoint nội dung công khai, giữ chữ ký hàm
// và thay phần thân bằng apiRequest (`@/lib/api/client`); component không cần đổi.
// Trang giới thiệu và bài viết đọc kho của màn Admin: chỉ bản có quyền "xem trên website"
// (tức đã đăng) mới hiện, đúng với BR-61.
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

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 200;

/** Nội dung đang được đăng lên website (admin có hành động "viewOnWebsite"). */
const isPublished = (item: { allowedActions: string[] }) =>
  item.allowedActions.includes("viewOnWebsite");

/**
 * Trang Giới thiệu công khai: bản cập nhật gần nhất trong số các trang đã đăng.
 * Không có trang nào đăng thì trả null; trang Giới thiệu vẫn hiện giờ và liên hệ.
 */
export async function getPublishedAboutPage(): Promise<PublicAboutPage | null> {
  await mockDelay(LATENCY_MS);
  const latest = websiteContentStore()
    .filter((item) => item.type === "ABOUT_PAGE" && isPublished(item))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
  if (!latest || latest.type !== "ABOUT_PAGE") return null;
  return { title: latest.title, body: latest.body };
}

/** Bài viết sức khỏe đã đăng; chưa đăng hoặc không có → 404 MSG-50. */
export async function getPublishedHealthArticle(
  id: string,
): Promise<PublicHealthArticle> {
  await mockDelay(LATENCY_MS);
  const found = websiteContentStore().find(
    (item) => item.id === id && item.type === "HEALTH_ARTICLE" && isPublished(item),
  );
  if (!found || found.type !== "HEALTH_ARTICLE") mockFail(404, "MSG-50");
  return {
    id: found.id,
    title: found.title,
    body: found.body,
    publishedAt: found.publishedAt,
  };
}
