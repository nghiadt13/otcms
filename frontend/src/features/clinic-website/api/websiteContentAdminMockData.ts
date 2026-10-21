import type {
  WebsiteContentAction,
  WebsiteContentDetail,
} from "../types/websiteContentAdmin";

// Dữ liệu giả cho màn quản trị nội dung website: 1 Thông tin phòng khám,
// 2 Trang giới thiệu, 23 Bài viết (2 trang khi size = 20).
// Cách kích hoạt lỗi để thử trên màn: xem đầu `websiteContentAdminApi.ts`.

export const NOT_FOUND_CONTENT_ID = "00000000-0000-0000-0000-000000000404";
export const SERVICE_ERROR_QUERY = "loi-dich-vu";
export const SERVICE_ERROR_TITLE = "Lỗi dịch vụ";
export const MOCK_ADMIN_NAME = "Trần Minh Quân";

const ADMIN_NAMES = [MOCK_ADMIN_NAME, "Lê Thu Hà"];
const BOTH: WebsiteContentAction[] = ["edit", "viewOnWebsite"];

const articleSeeds = [
  {
    title: "Chăm sóc cột sống khi làm việc văn phòng",
    body: "Ngồi lâu quá 45 phút là nên đứng dậy.\n\n## Thói quen nên có\n\n- Đi lại 5 phút sau mỗi giờ làm việc\n- Chỉnh màn hình ngang tầm mắt\n- Giữ lưng tựa hẳn vào ghế\n\nNếu đau lưng kéo dài trên **2 tuần**, hãy đặt lịch khám.",
  },
  {
    title: "Sau chấn thương cổ chân: khi nào cần chụp X-quang",
    body: "Không phải mọi cú trật cổ chân đều cần chụp phim.\n\n- Không thể đặt chân xuống đất đi bốn bước\n- Đau ở xương mắt cá khi ấn vào\n\nBác sĩ sẽ đánh giá trước khi chỉ định.",
  },
  {
    title: "Tập vật lý trị liệu tại nhà an toàn",
    body: "Tập đều mỗi ngày hiệu quả hơn tập dồn.\n\n1. Khởi động nhẹ 5 phút\n2. Làm đúng bài bác sĩ hướng dẫn\n3. Dừng ngay nếu đau tăng",
  },
  {
    title: "Loãng xương: ai cần tầm soát sớm",
    body: "Phụ nữ sau mãn kinh và người trên **65 tuổi** nên đo mật độ xương định kỳ.",
  },
  {
    title: "Đau khớp gối khi leo cầu thang",
    body: "Đau khớp gối khi leo cầu thang thường liên quan đến sụn khớp hoặc cơ đùi yếu.\n\n- Giảm cân nếu thừa cân\n- Tránh ngồi xổm kéo dài",
  },
  {
    title: "Chuẩn bị gì trước buổi khám chấn thương chỉnh hình",
    body: "Mang theo phim chụp cũ, đơn thuốc đang dùng và mô tả rõ thời điểm bắt đầu đau.",
  },
];

function uuid(n: number) {
  return `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
}

/** Thời điểm cố định (giờ VN, offset +07:00) lùi `daysAgo` ngày từ 18/10/2026. */
function isoAt(daysAgo: number, hour: number, minute = 0) {
  const date = new Date(Date.UTC(2026, 9, 18 - daysAgo, hour - 7, minute));
  return date.toISOString().replace(/\.\d{3}Z$/, "+00:00");
}

export function buildWebsiteContentSeed(): WebsiteContentDetail[] {
  const articles = Array.from({ length: 23 }, (_, i): WebsiteContentDetail => {
    const seed = articleSeeds[i % articleSeeds.length];
    const round = Math.floor(i / articleSeeds.length);
    return {
      id: uuid(100 + i),
      type: "HEALTH_ARTICLE",
      title: round === 0 ? seed.title : `${seed.title} (phần ${round + 1})`,
      body: seed.body,
      imageUrl: i % 3 === 0 ? `/media/website/2026/09/bai-viet-${i + 1}.jpg` : null,
      publishedAt: isoAt(40 + i * 2, 8, 10),
      updatedAt: isoAt(i * 2, 14, 30),
      publishedBy: ADMIN_NAMES[i % ADMIN_NAMES.length],
      // Bài thứ 4 chưa có trang công khai: chỉ cho sửa.
      allowedActions: i === 3 ? ["edit"] : [...BOTH],
    };
  });

  const clinicInfo: WebsiteContentDetail = {
    id: uuid(1),
    type: "CLINIC_INFO",
    title: "Thông tin phòng khám chính",
    publishedAt: isoAt(140, 9),
    updatedAt: isoAt(16, 10, 5),
    publishedBy: MOCK_ADMIN_NAME,
    allowedActions: [...BOTH],
    body: {
      clinicName: "Phòng khám OTCMS",
      address: "12 Nguyễn Thị Minh Khai, Quận 1, TP. Hồ Chí Minh",
      hotline: "028 3822 1234",
      email: "lienhe@example.com",
      openingHours: ["Thứ 2 – Thứ 6: 7:30 – 17:00", "Thứ 7: 7:30 – 12:00"],
    },
  };

  const aboutPages: WebsiteContentDetail[] = [
    {
      id: uuid(2),
      type: "ABOUT_PAGE",
      title: "Giới thiệu phòng khám",
      body: "# Về chúng tôi\n\nPhòng khám chuyên chấn thương chỉnh hình, kết nối đặt lịch, khám và xem kết quả trực tuyến.",
      publishedAt: isoAt(140, 9, 20),
      updatedAt: isoAt(30, 9, 45),
      publishedBy: MOCK_ADMIN_NAME,
      allowedActions: [...BOTH],
    },
    {
      id: uuid(3),
      type: "ABOUT_PAGE",
      title: "Đội ngũ bác sĩ và trang thiết bị",
      body: "Đội ngũ bác sĩ chuyên khoa cùng hệ thống chẩn đoán hình ảnh tại chỗ.\n\n- X-quang kỹ thuật số\n- Phòng xét nghiệm",
      publishedAt: isoAt(120, 10),
      updatedAt: isoAt(60, 11),
      publishedBy: "Lê Thu Hà",
      allowedActions: [...BOTH],
    },
  ];

  return [clinicInfo, ...aboutPages, ...articles];
}

let store: WebsiteContentDetail[] = buildWebsiteContentSeed();

export const websiteContentStore = () => store;

/** Đặt lại kho giả (test; thử ca rỗng bằng `resetWebsiteContentStore([])`). */
export function resetWebsiteContentStore(
  items: WebsiteContentDetail[] = buildWebsiteContentSeed(),
) {
  store = items;
}
