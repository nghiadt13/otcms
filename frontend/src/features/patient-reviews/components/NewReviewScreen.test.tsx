import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { addDays, formatDate, todayIsoDate } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetMyReviewMockData } from "../api/myReviewMockData";
import { NewReviewScreen } from "./NewReviewScreen";

const replace = vi.fn();
let search = "";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(search),
}));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

beforeEach(() => {
  search = "";
  resetLinkedProfilesMock();
  resetMyReviewMockData();
  replace.mockClear();
  vi.clearAllMocks();
});

describe("NewReviewScreen", () => {
  it("lượt có thể đánh giá: tiêu đề ghi ngày khám và người khám, mở form", async () => {
    search = "visitId=vis-an-done";
    render(<NewReviewScreen />);
    const expectedDate = formatDate(addDays(todayIsoDate(), -14));
    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: `Đánh giá lượt khám ngày ${expectedDate}`,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Nguyễn Văn An · Bản thân · Khám chỉnh hình tổng quát"),
    ).toBeInTheDocument();
    expect(screen.getByRole("radiogroup")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("lượt đã đánh giá: báo MSG-56 và về danh sách, không hiện form", async () => {
    search = "visitId=vis-lan-prev";
    render(<NewReviewScreen />);
    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith(
        expect.objectContaining({ code: "MSG-56" }),
      ),
    );
    expect(replace).toHaveBeenCalledWith("/portal/reviews");
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("thiếu mã lượt trên URL: coi là không hợp lệ, về danh sách", async () => {
    render(<NewReviewScreen />);
    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/portal/reviews"),
    );
    expect(notifyError).toHaveBeenCalledTimes(1);
  });

  it("lỗi tải danh sách lượt: báo lỗi có nút Thử lại; bấm lại thì mở form", async () => {
    search = "visitId=vis-an-done";
    resetMyReviewMockData({ failList: true });
    render(<NewReviewScreen />);
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không tải được lượt khám.");

    resetMyReviewMockData();
    await userEvent.click(
      within(alert).getByRole("button", { name: "Thử lại" }),
    );
    expect(await screen.findByRole("radiogroup")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
