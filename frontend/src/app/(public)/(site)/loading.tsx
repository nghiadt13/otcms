import { PublicPageSkeleton } from "@/components/brand/PublicPageSkeleton";

// Mặc định cho các trang danh sách của nhóm công khai; trang chi tiết có loading riêng.
export default function SiteLoading() {
  return <PublicPageSkeleton variant="list" />;
}
