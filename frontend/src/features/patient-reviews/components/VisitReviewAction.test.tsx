import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetMyReviewMockData } from "../api/myReviewMockData";
import { VisitReviewAction } from "./VisitReviewAction";

vi.mock("@/lib/api/client", async () => await import("../api/reviewTestClient"));

describe("VisitReviewAction", () => {
  beforeEach(() => {
    resetLinkedProfilesMock();
    resetMyReviewMockData();
  });

  it("chỉ dẫn đến biểu mẫu của lượt có trong danh sách đủ điều kiện từ API", async () => {
    render(
      <>
        <VisitReviewAction visitId="vis-an-done" />
        <VisitReviewAction visitId="vis-an-prev" />
      </>,
    );

    const link = await screen.findByRole("link", { name: "Viết đánh giá" });
    expect(link).toHaveAttribute("href", "/portal/reviews/new?visitId=vis-an-done");
    expect(screen.getAllByRole("link", { name: "Viết đánh giá" })).toHaveLength(1);
  });
});
