/* eslint-disable @next/next/no-img-element -- Clinic-uploaded images are served by the backend. */
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { formatDate } from "@/lib/format/formatDate";
import type { PublicArticleSummary } from "../types/websiteContent";
export function HomeHealthArticles({
  articles,
}: {
  articles: PublicArticleSummary[];
}) {
  if (!articles.length) return null;
  return (
    <section
      aria-labelledby="health-articles-title"
      className="bg-background py-16"
    >
      <div className={publicContainerClass}>
        <h2 id="health-articles-title" className="text-3xl font-semibold">
          Thông tin sức khỏe
        </h2>
        <div className="mt-8 grid gap-6 md:grid-cols-3">
          {articles.map((article) => (
            <article
              key={article.id}
              className="overflow-hidden rounded-xl border bg-card"
            >
              {article.imageUrl && (
                <img
                  src={article.imageUrl}
                  alt=""
                  className="aspect-video w-full object-cover"
                />
              )}
              <div className="space-y-3 p-5">
                <time
                  className="text-sm text-muted-foreground"
                  dateTime={article.publishedAt}
                >
                  {formatDate(article.publishedAt)}
                </time>
                <h3 className="text-xl font-semibold">
                  <Link
                    href={`/bai-viet/${article.id}`}
                    className="hover:text-primary"
                  >
                    {article.title}
                  </Link>
                </h3>
                {article.summary && (
                  <p className="text-muted-foreground">{article.summary}</p>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
