import type { Metadata } from "next";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { readPageIndex } from "@/lib/api/readPageIndex";
import { listPublicReviews } from "@/features/patient-reviews/api/publicReviewApi";
import { PublicReviewList } from "@/features/patient-reviews/components/PublicReviewList";

export const metadata: Metadata = { title: "Đánh giá từ bệnh nhân | OTCMS" };

const REVIEWS_PAGE_SIZE = 10;

// UC60 Xem đánh giá công khai: chỉ đánh giá đã duyệt và phản hồi còn công khai. Trang đọc từ
// `?page=` (đánh số từ 1); giá trị sai thì về trang đầu.
export default async function ReviewsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { page } = await searchParams;
  const result = await listPublicReviews({
    page: readPageIndex(page),
    size: REVIEWS_PAGE_SIZE,
  });

  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicPageHeader
        description="Đánh giá phòng khám đã duyệt, kèm phản hồi của phòng khám khi có."
        title="Đánh giá từ bệnh nhân"
      />
      <PublicReviewList page={result} />
    </div>
  );
}
