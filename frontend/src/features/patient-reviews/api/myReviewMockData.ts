import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { MyReviewStatus } from "../types/myReview";

// DỮ LIỆU GIẢ đánh giá của bệnh nhân (UC59). Lượt khám đặt theo mã hồ sơ (BN-...) và mã lượt khớp với
// mock lượt khám, kết quả (phase 05, 07). Chỉ lượt `completed` mới đánh giá được (BR-54).

export interface MockReviewVisit {
  visitId: string;
  /** Mã hồ sơ (BN-000123); API tìm hồ sơ đã gắn có mã này. */
  patientCode: string;
  /** YYYY-MM-DD */
  visitDate: string;
  serviceName: string;
  completed: boolean;
}

export interface MockReviewRecord {
  id: string;
  visitId: string;
  rating: 1 | 2 | 3 | 4 | 5;
  text: string;
  status: MyReviewStatus;
  clinicReply: string | null;
  /** ISO có offset +07:00 */
  createdAt: string;
}

export const myReviewStore: {
  visits: MockReviewVisit[];
  reviews: MockReviewRecord[];
  /** true: các lần tải danh sách trả 500 MSG-04. */
  failList: boolean;
} = { visits: [], reviews: [], failList: false };

function seedVisits(today: string): MockReviewVisit[] {
  return [
    {
      visitId: "vis-an-done",
      patientCode: "BN-000123",
      visitDate: addDays(today, -14),
      serviceName: "Khám chỉnh hình tổng quát",
      completed: true,
    },
    {
      visitId: "vis-an-prev",
      patientCode: "BN-000123",
      visitDate: "2026-06-12",
      serviceName: "Khám tổng quát",
      completed: true,
    },
    {
      visitId: "vis-an-today",
      patientCode: "BN-000123",
      visitDate: today,
      serviceName: "Khám chỉnh hình tổng quát",
      completed: false,
    },
    {
      visitId: "vis-lan-prev",
      patientCode: "BN-000456",
      visitDate: "2026-07-03",
      serviceName: "Khám tổng quát",
      completed: true,
    },
    {
      visitId: "vis-lan-incomplete",
      patientCode: "BN-000456",
      visitDate: addDays(today, -16),
      serviceName: "Khám tổng quát",
      completed: false,
    },
  ];
}

// Hai đánh giá đã gửi: một đang hiển thị có phản hồi, một đang chờ duyệt. Lượt `vis-an-done` còn trống.
const SEED_REVIEWS: MockReviewRecord[] = [
  {
    id: "rv-an-prev",
    visitId: "vis-an-prev",
    rating: 4,
    text: "Bác sĩ tư vấn kỹ, giải thích rõ bài tập cho cổ và vai. Phòng khám sạch sẽ, không phải chờ lâu.",
    status: "PUBLISHED",
    clinicReply:
      "Cảm ơn anh đã tin tưởng phòng khám. Chúc anh mau khỏe và tập đều các bài đã hướng dẫn.",
    createdAt: "2026-06-20T09:15:00+07:00",
  },
  {
    id: "rv-lan-prev",
    visitId: "vis-lan-prev",
    rating: 3,
    text: "Chờ khá lâu ở khâu lấy số. Mong phòng khám có thêm người hướng dẫn vào giờ cao điểm.",
    status: "PENDING",
    clinicReply: null,
    createdAt: "2026-10-22T20:10:00+07:00",
  },
];

/** Đặt lại kho giả về dữ liệu gốc (dùng trong test và demo). */
export function resetMyReviewMockData(options: { failList?: boolean } = {}) {
  myReviewStore.visits = seedVisits(todayIsoDate());
  myReviewStore.reviews = structuredClone(SEED_REVIEWS);
  myReviewStore.failList = options.failList ?? false;
}

resetMyReviewMockData();
