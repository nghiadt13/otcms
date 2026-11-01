import { cache } from "react";
import { publicApiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import type {
  PublicAboutPage,
  PublicHealthArticle,
  WebsiteContent,
} from "../types/websiteContent";
import { rethrowContentError } from "./contentApiError";
const root = "/api/v1/public" as const;
const url = (value: string | null | undefined) =>
  value?.startsWith("/api/") ? `${apiBaseUrl}${value}` : value;
export const getPublishedWebsiteContent = cache(
  async (): Promise<WebsiteContent> => {
    const result = await publicApiRequest<WebsiteContent>(
      `${root}/website-content`,
    ).catch(rethrowContentError);
    return {
      ...result,
      articles: result.articles?.map((article) => ({
        ...article,
        imageUrl: url(article.imageUrl),
      })),
    };
  },
);
export async function getPublishedAboutPage(): Promise<PublicAboutPage | null> {
  try {
    return await publicApiRequest<PublicAboutPage | null>(`${root}/about`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    return rethrowContentError(error);
  }
}
export async function getPublishedHealthArticle(
  id: string,
): Promise<PublicHealthArticle> {
  const article = await publicApiRequest<PublicHealthArticle>(
    `${root}/articles/${encodeURIComponent(id)}`,
  ).catch(rethrowContentError);
  return { ...article, imageUrl: url(article.imageUrl) };
}
