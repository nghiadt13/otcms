import { render, screen, within } from "@testing-library/react";
import { renderToReadableStream } from "react-dom/server";
import { expect, it, vi } from "vitest";
import { publicApiRequest } from "@/lib/api/client";
import type { PublicReview } from "../types/publicReview";
import { FeaturedReviews, FeaturedReviewsSection } from "./FeaturedReviews";

vi.mock("@/lib/api/client", () => ({ publicApiRequest: vi.fn() }));

const review: PublicReview = {
  id: "review-1",
  rating: 5,
  text: "Khám đúng giờ.",
  createdAt: "2026-11-04T10:00:00+07:00",
  clinicReply: null,
};

it("shows the published review, two placeholders and the public review list link", () => {
  render(<FeaturedReviews reviews={[review]} />);

  const section = screen.getByRole("region", {
    name: "Bệnh nhân nói gì về chúng tôi",
  });
  expect(section).toHaveTextContent(review.text);
  expect(within(section).getAllByRole("listitem")).toHaveLength(3);
  expect(
    within(section).getAllByText("Đánh giá đang chờ cập nhật"),
  ).toHaveLength(2);
  expect(
    within(section).getAllByRole("img", { name: /trên 5 sao/ }),
  ).toHaveLength(1);
  expect(section.querySelector("time")).toHaveAttribute(
    "datetime",
    review.createdAt,
  );
  expect(
    screen.getByRole("link", { name: "Xem tất cả đánh giá" }),
  ).toHaveAttribute("href", "/reviews");
});

it("shows only the first three reviews and includes replies beyond the first card", () => {
  render(
    <FeaturedReviews
      reviews={[
        review,
        {
          ...review,
          id: "review-2",
          text: "Bác sĩ tư vấn rõ ràng.",
          clinicReply: "Cảm ơn bạn đã phản hồi.",
        },
        { ...review, id: "review-3", text: "Phòng khám sạch sẽ." },
        { ...review, id: "review-4", text: "Đánh giá cũ hơn." },
      ]}
    />,
  );

  const cards = screen.getAllByRole("listitem");
  expect(cards).toHaveLength(3);
  expect(cards[1]).toHaveTextContent("Cảm ơn bạn đã phản hồi.");
  expect(screen.queryByText("Đánh giá cũ hơn.")).not.toBeInTheDocument();
});

it.each([
  ["empty response", "Đánh giá đang chờ cập nhật"],
  ["unavailable endpoint", "Chưa tải được dữ liệu đánh giá"],
] as const)("shows three status cards for an %s", async (response, message) => {
  const request = vi.mocked(publicApiRequest).mockReset();
  if (response === "empty response") request.mockResolvedValue({ items: [] });
  else request.mockRejectedValue(new TypeError("fetch failed"));

  const stream = await renderToReadableStream(<FeaturedReviewsSection />);
  await stream.allReady;
  const html = await new Response(stream).text();
  const content = new DOMParser().parseFromString(html, "text/html").body;

  const section = within(content).getByRole("region", {
    name: "Bệnh nhân nói gì về chúng tôi",
  });
  expect(within(section).getAllByRole("listitem")).toHaveLength(3);
  expect(within(section).getAllByText(message)).toHaveLength(3);
  expect(within(section).queryByRole("img", { name: /trên 5 sao/ })).toBeNull();
  expect(request).toHaveBeenCalledWith(
    "/api/v1/public/reviews?page=0&size=3&sort=submittedAt%2Cdesc",
    { signal: expect.any(AbortSignal) },
  );
});
