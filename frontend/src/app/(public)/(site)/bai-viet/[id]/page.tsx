import type { Metadata } from "next";
import { cache } from "react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { loadOrNotFound } from "@/lib/api/loadOrNotFound";
import { getPublishedHealthArticle } from "@/features/clinic-website/api/websiteContentApi";
import { HealthArticleView } from "@/features/clinic-website/components/HealthArticleView";

type Props = { params: Promise<{ id: string }> };

// Bài viết sức khỏe đã đăng (`/bai-viet/{id}`). Bài chưa đăng hoặc không có → 404 MSG-50.
const loadArticle = cache((id: string) =>
  loadOrNotFound(() => getPublishedHealthArticle(id)),
);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const article = await loadArticle(id);
  return { title: `${article.title} | Bài viết sức khỏe` };
}

export default async function HealthArticlePage({ params }: Props) {
  const { id } = await params;
  const article = await loadArticle(id);
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <HealthArticleView article={article} />
    </div>
  );
}
