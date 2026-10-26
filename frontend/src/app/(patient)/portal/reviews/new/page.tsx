import { Suspense } from "react";
import {
  NewReviewScreen,
  ReviewFormSkeleton,
} from "@/features/patient-reviews/components/NewReviewScreen";

export const metadata = {
  title: "Viết đánh giá | OTCMS",
};

// Viết đánh giá một lượt khám. Mã lượt đọc từ URL (?visitId=) nên form nằm trong Suspense.
export default function NewReviewPage() {
  return (
    <Suspense fallback={<ReviewFormSkeleton />}>
      <NewReviewScreen />
    </Suspense>
  );
}
