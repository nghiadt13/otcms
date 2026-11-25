import Link from "next/link";
import { notFound } from "next/navigation";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { listPublishedHealthArticles } from "@/features/clinic-website/api/websiteContentApi";
import { HomeHealthArticles } from "@/features/clinic-website/components/HomeHealthArticles";

export const metadata = { title: "Bài viết sức khỏe | OTCMS" };

export default async function ArticlesPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string | string[] }>;
}) {
  const page = Number((await searchParams).page ?? "1");
  if (!Number.isSafeInteger(page) || page < 1 || page > 178956970) notFound();

  const result = await listPublishedHealthArticles(page - 1);
  if (page > Math.max(1, result.totalPages)) notFound();

  if (!result.items.length) {
    return (
      <div className={`${publicContainerClass} py-12`}>
        <h1 className="text-2xl font-semibold">Thông tin sức khỏe</h1>
        <p className="mt-4">Chưa có bài viết nào được đăng.</p>
      </div>
    );
  }

  return (
    <>
      <HomeHealthArticles articles={result.items} archive />
      {result.totalPages > 1 && (
        <nav
          aria-label="Phân trang bài viết"
          className={`${publicContainerClass} mb-12 flex flex-wrap items-center gap-4`}
        >
          {page > 1 && (
            <Link
              className={publicButtonClass("secondary")}
              href={`/bai-viet?page=${page - 1}`}
            >
              Trang trước
            </Link>
          )}
          <span>
            Trang {page} / {result.totalPages}
          </span>
          {page < result.totalPages && (
            <Link
              className={publicButtonClass("secondary")}
              href={`/bai-viet?page=${page + 1}`}
            >
              Trang sau
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
