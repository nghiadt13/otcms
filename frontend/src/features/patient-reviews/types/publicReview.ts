import type { PageResponse } from "@/lib/api/pagination";

// Đánh giá đã duyệt và đang hiển thị công khai (BR-55). Không có tên người viết: trang
// công khai không lộ danh tính bệnh nhân. `clinicReply` chỉ có khi đánh giá còn công khai
// (BR-56).
export type PublicReview = {
  id: string;
  rating: 1 | 2 | 3 | 4 | 5;
  /** Nội dung chữ; có thể rỗng khi bệnh nhân chỉ chấm sao. */
  text: string;
  /** ISO có offset +07:00: lúc gửi đánh giá. */
  createdAt: string;
  clinicReply: string | null;
};

export type ReviewSummary = {
  /** Trung bình sao, làm tròn 1 chữ số thập phân; 0 khi chưa có đánh giá. */
  average: number;
  count: number;
};

export type PublicReviewPage = PageResponse<
  PublicReview,
  { summary: ReviewSummary }
>;
