import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetMyReviewMockData } from "../api/myReviewMockData";
import { submitReview } from "../api/myReviewApi";
import type { ReviewableVisit } from "../types/myReview";
import { ReviewForm } from "./ReviewForm";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
}));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

const VISIT: ReviewableVisit = {
  visitId: "vis-an-done",
  visitDate: "2026-10-10",
  serviceName: "Khám chỉnh hình tổng quát",
  patient: { fullName: "Nguyễn Văn An", relationship: "SELF" },
};
const GOOD_TEXT = "Bác sĩ khám kỹ và giải thích rõ từng bước.";

const textbox = () =>
  screen.getByRole("textbox", { name: "Chia sẻ trải nghiệm của bạn" });
const submitButton = () => screen.getByRole("button", { name: "Gửi đánh giá" });

async function fillValid(stars: string) {
  await userEvent.click(screen.getByRole("radio", { name: stars }));
  await userEvent.type(textbox(), GOOD_TEXT);
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetMyReviewMockData();
  replace.mockClear();
  vi.clearAllMocks();
});

describe("ReviewForm", () => {
  it("nút Gửi bị khóa tới khi chọn điểm và nội dung đủ 10 ký tự; có bộ đếm ký tự", async () => {
    render(<ReviewForm visit={VISIT} />);
    expect(submitButton()).toBeDisabled();

    await userEvent.click(
      screen.getByRole("radio", { name: "5 sao · Rất hài lòng" }),
    );
    expect(submitButton()).toBeDisabled();

    await userEvent.type(textbox(), "Ngắn");
    expect(screen.getByText("4/1000")).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();

    await userEvent.type(textbox(), " quá, nhưng đủ dài");
    expect(submitButton()).toBeEnabled();
  });

  it("chọn điểm bằng bàn phím: nhóm radio có tên, mũi tên chuyển mức", async () => {
    render(<ReviewForm visit={VISIT} />);
    expect(
      screen.getByRole("radiogroup", { name: "Bạn hài lòng ở mức nào?" }),
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("radio", { name: "3 sao · Bình thường" }),
    );
    await userEvent.keyboard("{ArrowRight}");
    expect(
      screen.getByRole("radio", { name: "4 sao · Hài lòng" }),
    ).toBeChecked();
  });

  it("gửi thành công: báo MSG-55 và về danh sách đánh giá", async () => {
    render(<ReviewForm visit={VISIT} />);
    await fillValid("4 sao · Hài lòng");
    await userEvent.click(submitButton());

    await waitFor(() => expect(notifySuccess).toHaveBeenCalledWith("MSG-55"));
    expect(replace).toHaveBeenCalledWith("/portal/reviews");
  });

  it("lượt đã có đánh giá (409): báo MSG-56 và về danh sách", async () => {
    await submitReview({ visitId: "vis-an-done", rating: 4, text: GOOD_TEXT });
    render(<ReviewForm visit={VISIT} />);
    await fillValid("4 sao · Hài lòng");
    await userEvent.click(submitButton());

    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith(
        expect.objectContaining({ code: "MSG-56" }),
      ),
    );
    expect(replace).toHaveBeenCalledWith("/portal/reviews");
    expect(notifySuccess).not.toHaveBeenCalled();
  });
});
