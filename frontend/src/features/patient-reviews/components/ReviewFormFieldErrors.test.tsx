import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { resetMyReviewMockData } from "../api/myReviewMockData";
import { submitReview } from "../api/myReviewApi";
import type { ReviewableVisit } from "../types/myReview";
import { ReviewForm } from "./ReviewForm";

// Ranh giới API được thay bằng bản gọi thật, chỉ để ép một lần trả 400 (BE từ chối dù FE đã kiểm).
vi.mock("../api/myReviewApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/myReviewApi")>();
  return { ...actual, submitReview: vi.fn(actual.submitReview) };
});
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

beforeEach(() => {
  resetMyReviewMockData();
  replace.mockClear();
  vi.clearAllMocks();
});

async function submitWithStars() {
  await userEvent.click(
    screen.getByRole("radio", { name: "4 sao · Hài lòng" }),
  );
  await userEvent.type(
    screen.getByRole("textbox", { name: "Chia sẻ trải nghiệm của bạn" }),
    "Bác sĩ khám kỹ và giải thích rõ từng bước.",
  );
  await userEvent.click(screen.getByRole("button", { name: "Gửi đánh giá" }));
}

describe("ReviewForm — lỗi 400", () => {
  it("BE báo lỗi nội dung: MSG-57 hiện ngay dưới ô nội dung, ô đánh dấu không hợp lệ, form còn dùng được", async () => {
    vi.mocked(submitReview).mockRejectedValueOnce(
      new ApiError(400, {
        code: "MSG-57",
        fieldErrors: [{ field: "text", code: "MSG-57" }],
      }),
    );
    render(<ReviewForm visit={VISIT} />);
    await submitWithStars();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(formatAppMessage("MSG-57"));
    expect(
      screen.getByRole("textbox", { name: "Chia sẻ trải nghiệm của bạn" }),
    ).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("button", { name: "Gửi đánh giá" })).toBeEnabled();
    expect(replace).not.toHaveBeenCalled();
    expect(notifyError).not.toHaveBeenCalled();
  });

  it("400 không gắn trường nào: vẫn báo MSG-57 dưới ô nội dung", async () => {
    vi.mocked(submitReview).mockRejectedValueOnce(
      new ApiError(400, { code: "MSG-57" }),
    );
    render(<ReviewForm visit={VISIT} />);
    await submitWithStars();
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        formatAppMessage("MSG-57"),
      ),
    );
  });

  it("lỗi 500: báo toast MSG-04, không về danh sách", async () => {
    vi.mocked(submitReview).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<ReviewForm visit={VISIT} />);
    await submitWithStars();
    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith(
        expect.objectContaining({ status: 500 }),
      ),
    );
    expect(replace).not.toHaveBeenCalled();
  });
});
