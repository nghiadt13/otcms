import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as api from "../api/reviewModerationApi";
import {
  resetReviewModerationMockData,
  reviewIdOf,
  reviewStore,
} from "../api/reviewModerationMockData";
import { ReviewModerationManagement } from "./ReviewModerationManagement";

// Bọc API để đếm lời gọi mà vẫn chạy mock thật bên dưới.
vi.mock("../api/reviewModerationApi", async (original) => {
  const real = await original<typeof import("../api/reviewModerationApi")>();
  return {
    ...real,
    listModerationReviews: vi.fn(real.listModerationReviews),
    getModerationReview: vi.fn(real.getModerationReview),
    hideModerationReview: vi.fn(real.hideModerationReview),
  };
});

const SLOW = { timeout: 5000 };

function renderScreen() {
  return render(
    <>
      <Toaster />
      <ReviewModerationManagement />
    </>,
  );
}

const loaded = () => screen.findByText(/Trang 1\/2/, undefined, SLOW);

async function openRow(user: ReturnType<typeof userEvent.setup>, nn: number) {
  // Tên bệnh nhân lặp giữa các bản ghi nên chọn nút theo id.
  const selector = `[data-review-trigger="${reviewIdOf(nn)}"]`;
  await waitFor(
    () => expect(document.querySelector(selector)).not.toBeNull(),
    SLOW,
  );
  await user.click(document.querySelector<HTMLElement>(selector)!);
}

const sheet = () => screen.findByRole("dialog", undefined, SLOW);

beforeEach(() => {
  resetReviewModerationMockData();
  vi.mocked(api.listModerationReviews).mockClear();
  vi.mocked(api.getModerationReview).mockClear();
  vi.mocked(api.hideModerationReview).mockClear();
});

describe("ReviewModerationManagement", () => {
  it("mặc định tab Chờ duyệt, gọi list status=PENDING và hiện số đếm", async () => {
    renderScreen();
    await loaded();
    expect(vi.mocked(api.listModerationReviews).mock.calls[0][0]).toMatchObject(
      {
        status: "PENDING",
      },
    );
    expect(screen.getByRole("tab", { name: "Chờ duyệt 21" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "Tất cả 46" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Xóa/ }),
    ).not.toBeInTheDocument();
  });

  it("đổi tab gọi lại đúng status và về trang 0", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await user.click(screen.getByRole("button", { name: "Sau" }));
    await screen.findByText(/Trang 2\/2/, undefined, SLOW);

    await user.click(screen.getByRole("tab", { name: /Đang hiển thị/ }));
    await screen.findByText(/Trang 1\/1/, undefined, SLOW);
    const last = vi.mocked(api.listModerationReviews).mock.calls.at(-1)![0];
    expect(last).toMatchObject({ status: "PUBLISHED", page: 0 });
  });

  it("from > to hiện lỗi dưới ô ngày và không gọi list", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    const calls = vi.mocked(api.listModerationReviews).mock.calls.length;
    await user.type(screen.getByLabelText("Đến ngày"), "2026-10-01");
    await waitFor(() =>
      expect(vi.mocked(api.listModerationReviews).mock.calls.length).toBe(
        calls + 1,
      ),
    );
    await loaded();
    const after = vi.mocked(api.listModerationReviews).mock.calls.length;
    await user.type(screen.getByLabelText("Từ ngày"), "2026-10-10");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Tiêu chí lọc chưa hợp lệ",
    );
    expect(vi.mocked(api.listModerationReviews).mock.calls.length).toBe(after);
  });

  it("bấm dòng chờ duyệt mở Sheet có Duyệt và hiển thị + Từ chối, không có Ẩn đánh giá", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 1);
    const dialog = await sheet();
    await within(dialog).findByRole(
      "button",
      { name: "Duyệt và hiển thị" },
      SLOW,
    );
    expect(
      within(dialog).getByRole("button", { name: "Từ chối" }),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Ẩn đánh giá" }),
    ).toBeNull();
    expect(within(dialog).queryByRole("button", { name: /Xóa/ })).toBeNull();
  });

  it("nút chỉ theo allowedActions: thiếu approve thì không có nút duyệt", async () => {
    vi.mocked(api.getModerationReview).mockImplementationOnce(async (id) => {
      const detail = reviewStore.reviews.find((r) => r.id === id)!;
      return { ...structuredClone(detail), allowedActions: ["hide", "reply"] };
    });
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 1);
    const dialog = await sheet();
    await within(dialog).findByRole("button", { name: "Từ chối" }, SLOW);
    expect(
      within(dialog).queryByRole("button", { name: "Duyệt và hiển thị" }),
    ).toBeNull();
  });

  it("Từ chối qua hộp xác nhận: Hủy không gọi API, xác nhận gọi hide và báo thành công", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 1);
    const dialog = await sheet();
    await user.click(
      await within(dialog).findByRole("button", { name: "Từ chối" }, SLOW),
    );

    const confirm = await screen.findByRole("alertdialog");
    await user.click(within(confirm).getByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(api.hideModerationReview).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "Từ chối" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "Từ chối",
      }),
    );
    expect(
      await screen.findByText(
        "Đã cập nhật trạng thái hiển thị của đánh giá.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
    expect(api.hideModerationReview).toHaveBeenCalledTimes(1);
    expect(
      reviewStore.reviews.find((r) => r.id === reviewIdOf(1))!.status,
    ).toBe("HIDDEN");
  });

  it("phản hồi: ô rỗng không có nút Gửi; gõ chữ mới hiện; lỗi MSG-57 đỏ dưới ô", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 10); // NN=10: PUT reply luôn 400
    const dialog = await sheet();
    const box = await within(dialog).findByRole("textbox", undefined, SLOW);
    expect(
      within(dialog).queryByRole("button", { name: "Gửi phản hồi" }),
    ).toBeNull();
    expect(dialog).toContainElement(box); // composer nằm inline trong Sheet, không có modal thứ hai
    expect(screen.queryByRole("alertdialog")).toBeNull();

    await user.type(box, "Cảm ơn bạn");
    await user.click(
      within(dialog).getByRole("button", { name: "Gửi phản hồi" }),
    );
    const error = await within(dialog).findByText(
      /Phản hồi chưa hợp lệ/,
      undefined,
      SLOW,
    );
    expect(box).toHaveAttribute("aria-invalid", "true");
    expect(box.getAttribute("aria-describedby")).toContain(error.id);
  });

  it("phản hồi có sẵn: Sửa phản hồi mở composer điền sẵn nội dung cũ", async () => {
    const user = userEvent.setup();
    renderScreen();
    await user.click(
      await screen.findByRole("tab", { name: /Đang hiển thị/ }, SLOW),
    );
    await openRow(user, 6); // NN=06 đang hiển thị, đã phản hồi
    const dialog = await sheet();
    await user.click(
      await within(dialog).findByRole("button", { name: "Sửa phản hồi" }, SLOW),
    );
    expect(within(dialog).getByRole("textbox")).toHaveValue(
      reviewStore.reviews.find((r) => r.id === reviewIdOf(6))!.reply!.text,
    );
    expect(
      within(dialog).getByRole("button", { name: "Hủy" }),
    ).toBeInTheDocument();
  });

  it("duyệt gặp 409 hiện lỗi MSG-56 và tải lại chi tiết", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 9); // NN=09: mọi quyết định đều 409
    const dialog = await sheet();
    const getCalls = vi.mocked(api.getModerationReview).mock.calls.length;
    await user.click(
      await within(dialog).findByRole(
        "button",
        { name: "Duyệt và hiển thị" },
        SLOW,
      ),
    );
    expect(
      await screen.findByText(/Trạng thái đã thay đổi/, undefined, SLOW),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(vi.mocked(api.getModerationReview).mock.calls.length).toBe(
        getCalls + 1,
      ),
    );
  });

  it("GET chi tiết lỗi 500 hiện thông báo và Thử lại trong Sheet", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openRow(user, 12);
    const dialog = await sheet();
    expect(
      await within(dialog).findByText(
        "Không mở được đánh giá này.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Thử lại" }),
    ).toBeInTheDocument();
  });
});
