import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetMyReviewMockData } from "../api/myReviewMockData";
import { MyReviewsPage } from "./MyReviewsPage";

beforeEach(() => {
  resetLinkedProfilesMock();
  resetMyReviewMockData();
});

describe("MyReviewsPage", () => {
  it("lượt có thể đánh giá có nút Viết đánh giá dẫn tới form; đánh giá đã gửi có trạng thái và phản hồi", async () => {
    render(<MyReviewsPage />);
    const writeLink = await screen.findByRole("link", {
      name: /Viết đánh giá lượt khám ngày .+ của Nguyễn Văn An/,
    });
    expect(writeLink).toHaveAttribute(
      "href",
      "/portal/reviews/new?visitId=vis-an-done",
    );
    expect(await screen.findByText("Đang hiển thị")).toBeInTheDocument();
    expect(screen.getByText("Chờ duyệt")).toBeInTheDocument();
    expect(screen.getByText("Phòng khám phản hồi")).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Thị Lan · Cha/Mẹ")).toBeInTheDocument();
  });

  it("hai khối có tiêu đề cấp 2 riêng, không có khối nào trống khi có dữ liệu", async () => {
    render(<MyReviewsPage />);
    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Lượt khám có thể đánh giá",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Đánh giá đã gửi" }),
    ).toBeInTheDocument();
  });

  it("không có hồ sơ đã gắn: cả hai khối đều hiện câu hướng dẫn", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<MyReviewsPage />);
    expect(
      await screen.findByText(/Hiện chưa có lượt khám nào để đánh giá/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Bạn chưa gửi đánh giá nào/)).toBeInTheDocument();
  });

  it("lỗi tải: báo lỗi có nút Thử lại; bấm lại thì danh sách hiện", async () => {
    resetMyReviewMockData({ failList: true });
    render(<MyReviewsPage />);
    const alerts = await screen.findAllByRole("alert");
    expect(alerts).toHaveLength(2);
    expect(alerts[0]).toHaveTextContent(
      "Không tải được lượt khám có thể đánh giá.",
    );

    resetMyReviewMockData();
    await userEvent.click(
      within(alerts[0]).getByRole("button", { name: "Thử lại" }),
    );
    expect(
      await screen.findByRole("link", { name: /Viết đánh giá lượt khám/ }),
    ).toBeInTheDocument();
  });
});
