import type {
  ReviewAction,
  ReviewDetail,
  ReviewRating,
  ReviewStatus,
} from "../types/reviewModeration";

// DỮ LIỆU GIẢ: 46 đánh giá (21 chờ duyệt, 15 đang hiển thị, 10 đã ẩn). Tên người,
// bác sĩ, dịch vụ là hư cấu. Id: 0b3e5c2a-7d19-4f7e-9a1c-0000000000NN (NN = 01…46).

export const reviewIdOf = (nn: number) =>
  `0b3e5c2a-7d19-4f7e-9a1c-${String(nn).padStart(12, "0")}`;

/** NN kích hoạt lỗi (xem reviewModerationApi.ts). */
export const MOCK_CONFLICT_NN = 9; // approve/hide/unhide → 409 MSG-56
export const MOCK_REPLY_INVALID_NN = 10; // PUT reply → 400 MSG-57
export const MOCK_NOT_FOUND_NN = 11; // GET → 404 MSG-50
export const MOCK_SERVER_ERROR_NN = 12; // GET → 500 MSG-04

function range(from: number, to: number) {
  return Array.from({ length: to - from + 1 }, (_, i) => from + i);
}

const PUBLISHED_NN = new Set([6, 7, 8, ...range(15, 26)]);
const HIDDEN_NN = new Set([14, ...range(27, 35)]);
const REPLIED_NN = new Set([6, 14, 15, 16, 17, 24]);
const RATING_OVERRIDE: Record<number, ReviewRating> = { 1: 2, 6: 5, 14: 2 };
const EMPTY_CONTENT_NN = new Set([5, 40]);

const PATIENTS = [
  "Nguyễn Thị Mai",
  "Lê Thu Hằng",
  "Trần Văn Huy",
  "Phạm Quốc Khánh",
  "Vũ Ngọc Anh",
  "Đặng Minh Tuấn",
  "Hoàng Thị Lan",
  "Bùi Gia Bảo",
];
const DOCTORS = ["BS. Trần Quốc Bảo", "BS. Lê Minh Châu", "BS. Nguyễn Hải Đăng"];
const SERVICES = [
  "Khám chỉnh hình",
  "Chụp X-quang",
  "Xét nghiệm máu",
  "Chụp MRI khớp gối",
];
const CONTENTS = [
  "Chờ hơn một tiếng mới được gọi vào khám. Nhân viên không thông báo lý do.",
  "Bác sĩ khám kỹ và giải thích rõ từng bước. Phòng khám sạch sẽ.",
  "Đặt lịch trên web rất nhanh, đến nơi có người hướng dẫn ngay. Phim chụp xong xem được trên điện thoại.",
  "Bác sĩ giải thích kết quả MRI bằng hình ảnh nên tôi hiểu rõ tình trạng sụn chêm của mình. Y tá hướng dẫn tập phục hồi chức năng từng động tác, dặn lịch tái khám cụ thể. Tôi đã quay lại lần hai và vẫn hài lòng như lần đầu. Chỉ mong khu vực chờ có thêm ghế ngồi vào giờ cao điểm.",
  "Chi phí hơi cao so với dự kiến nhưng chất lượng tốt.",
  "Lấy máu nhẹ tay, có kết quả trong buổi sáng.",
];

/** Quy tắc nút theo trạng thái; `editReply` thay `reply` khi đã có phản hồi. */
export function allowedActionsOf(
  status: ReviewStatus,
  hasReply: boolean,
): ReviewAction[] {
  const reply: ReviewAction = hasReply ? "editReply" : "reply";
  if (status === "PENDING") return ["approve", "hide", reply];
  if (status === "PUBLISHED") return ["hide", reply];
  return ["unhide", reply];
}

function buildReview(nn: number): ReviewDetail {
  const status: ReviewStatus = PUBLISHED_NN.has(nn)
    ? "PUBLISHED"
    : HIDDEN_NN.has(nn)
      ? "HIDDEN"
      : "PENDING";
  // NN=1 gửi 19/10/2026, lùi dần tới 01/09/2026 ở NN=46.
  const day = Math.round(((nn - 1) * 48) / 45);
  const submittedDate = new Date(Date.UTC(2026, 9, 19 - day))
    .toISOString()
    .slice(0, 10);
  const visitDate = new Date(Date.UTC(2026, 9, 18 - day))
    .toISOString()
    .slice(0, 10);
  const hour = String(8 + (nn % 10)).padStart(2, "0");
  const minute = String((nn * 7) % 60).padStart(2, "0");
  const replied = REPLIED_NN.has(nn);
  return {
    id: reviewIdOf(nn),
    submittedAt: `${submittedDate}T${hour}:${minute}:00+07:00`,
    patientName: PATIENTS[nn % PATIENTS.length],
    rating: RATING_OVERRIDE[nn] ?? ((((nn * 3) % 5) + 1) as ReviewRating),
    content: EMPTY_CONTENT_NN.has(nn) ? "" : CONTENTS[nn % CONTENTS.length],
    status,
    replied,
    visit: {
      visitDate,
      doctorName: DOCTORS[nn % DOCTORS.length],
      serviceName: SERVICES[nn % SERVICES.length],
    },
    reply: replied
      ? {
          text: "Cảm ơn bạn đã tin tưởng phòng khám. Chúng tôi đã ghi nhận góp ý.",
          repliedAt: `${submittedDate}T17:30:00+07:00`,
        }
      : null,
    allowedActions: allowedActionsOf(status, replied),
  };
}

const seed = () => range(1, 46).map(buildReview);

/** Kho trong bộ nhớ; API giả đọc/ghi trực tiếp. */
export const reviewStore: { reviews: ReviewDetail[] } = { reviews: seed() };

export function resetReviewModerationMockData() {
  reviewStore.reviews = seed();
}
