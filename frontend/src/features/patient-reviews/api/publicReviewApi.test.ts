import { beforeEach, expect, it, vi } from "vitest";
import { publicApiRequest } from "@/lib/api/client";
import type { PublicReview } from "../types/publicReview";
import { listFeaturedReviews, listPublicReviews } from "./publicReviewApi";

vi.mock("@/lib/api/client", () => ({ publicApiRequest: vi.fn() }));

const request = vi.mocked(publicApiRequest);

beforeEach(() => {
  request.mockReset();
});

it("passes paging and sorting to the public endpoint", async () => {
  request.mockResolvedValue({ items: [] });
  await listPublicReviews({ page: 1, sort: "rating,desc" });
  expect(request).toHaveBeenCalledWith(
    "/api/v1/public/reviews?page=1&sort=rating%2Cdesc",
    { signal: undefined },
  );
});

it("requests the newest public reviews and returns at most three", async () => {
  const items = [4, 3, 2, 1].map((day): PublicReview => ({
    id: String(day),
    rating: 5,
    text: "Khám đúng giờ.",
    createdAt: `2026-11-0${day}T10:00:00+07:00`,
    clinicReply: null,
  }));
  request.mockResolvedValue({ items });

  await expect(listFeaturedReviews()).resolves.toEqual(items.slice(0, 3));
  expect(request).toHaveBeenCalledWith(
    "/api/v1/public/reviews?page=0&size=3&sort=submittedAt%2Cdesc",
    { signal: expect.any(AbortSignal) },
  );
});

it("returns an empty list when there are no public reviews", async () => {
  request.mockResolvedValue({ items: [] });

  await expect(listFeaturedReviews()).resolves.toEqual([]);
});

it("returns an empty list when the public endpoint is unavailable", async () => {
  request.mockRejectedValue(new TypeError("fetch failed"));

  await expect(listFeaturedReviews()).resolves.toEqual([]);
});

it(
  "aborts a slow request and returns an empty list",
  { timeout: 3_000 },
  async () => {
    request.mockImplementation(
      (_path, init) =>
        new Promise((_, reject) => {
          const signal = init?.signal;
          signal?.addEventListener("abort", () => reject(signal.reason), {
            once: true,
          });
        }),
    );

    await expect(listFeaturedReviews()).resolves.toEqual([]);
    expect(request.mock.calls[0][1]?.signal?.reason).toMatchObject({
      name: "TimeoutError",
    });
  },
);
