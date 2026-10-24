import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { formatDate } from "@/lib/format/formatDate";
import type { PublicHealthArticle } from "../types/websiteContent";
import { WebsiteContentBody } from "./WebsiteContentBody";

// Bài viết sức khỏe công khai (`/bai-viet/{id}`): tiêu đề, ngày đăng và nội dung Markdown
// đã lọc (WebsiteContentBody không dùng HTML thô).
export function HealthArticleView({ article }: { article: PublicHealthArticle }) {
  return (
    <article className="max-w-3xl">
      <PublicPageHeader title={article.title}>
        <time
          className="mt-6 block text-base text-muted-foreground"
          dateTime={article.publishedAt}
        >
          Đăng ngày {formatDate(article.publishedAt)}
        </time>
      </PublicPageHeader>
      <WebsiteContentBody className="mt-10 text-lg leading-8" markdown={article.body} />
    </article>
  );
}
