import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PublicReviewPage } from "../types/publicReview";
import { PublicReviewList } from "./PublicReviewList";

function pageOf(overrides: Partial<PublicReviewPage>): PublicReviewPage {
  return {
    items: [
      {
        id: "rv-1",
        rating: 5,
        text: "Bác sĩ giải thích rõ ràng.",
        createdAt: "2026-10-19T08:30:00+07:00",
        clinicReply: "Cảm ơn bạn đã tin tưởng phòng khám.",
      },
      {
        id: "rv-2",
        rating: 4,
        text: "",
        createdAt: "2026-10-18T09:00:00+07:00",
        clinicReply: null,
      },
    ],
    page: 0,
    size: 10,
    totalItems: 12,
    totalPages: 2,
    summary: { average: 4.5, count: 12 },
    ...overrides,
  };
}

describe("PublicReviewList", () => {
  it("đánh giá có phản hồi hiện khối 'Phản hồi từ phòng khám', đánh giá không có thì không hiện", () => {
    render(<PublicReviewList page={pageOf({})} />);
    expect(screen.getAllByText("Phản hồi từ phòng khám")).toHaveLength(1);
    expect(screen.getByText("Cảm ơn bạn đã tin tưởng phòng khám.")).toBeInTheDocument();
    expect(screen.getByText("Bác sĩ giải thích rõ ràng.")).toBeInTheDocument();
  });

  it("ghi điểm trung bình và số đánh giá đã duyệt", () => {
    const { container } = render(<PublicReviewList page={pageOf({})} />);
    expect(container).toHaveTextContent("Điểm trung bình 4,5/5 từ 12 đánh giá đã duyệt");
  });

  it("còn trang sau thì có 'Xem thêm đánh giá' trỏ tới trang kế (?page= đánh số từ 1)", () => {
    render(<PublicReviewList page={pageOf({ page: 0, totalPages: 2 })} />);
    expect(screen.getByRole("link", { name: "Xem thêm đánh giá" })).toHaveAttribute(
      "href",
      "/reviews?page=2",
    );
  });

  it("trang cuối không có 'Xem thêm đánh giá'", () => {
    render(<PublicReviewList page={pageOf({ page: 1, totalPages: 2 })} />);
    expect(screen.queryByRole("link", { name: "Xem thêm đánh giá" })).toBeNull();
  });

  it("chưa có đánh giá nào thì hiện câu trống", () => {
    render(
      <PublicReviewList
        page={pageOf({ items: [], totalItems: 0, totalPages: 1, summary: { average: 0, count: 0 } })}
      />,
    );
    expect(screen.getByText("Chưa có đánh giá nào.")).toBeInTheDocument();
  });
});
