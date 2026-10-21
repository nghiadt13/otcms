import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { accountStore, resetAccountMockStore } from "../api/accountMockData";
import { AccountManagement } from "./AccountManagement";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const renderScreen = () =>
  render(
    <>
      <Toaster />
      <AccountManagement />
    </>,
  );

// Debounce 250 ms + độ trễ API giả: để dư thời gian cho máy chậm.
const SLOW = { timeout: 4000 };
const loaded = () => screen.findByText(/Trang 1\/3/, undefined, SLOW);
async function search(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(searchBox());
  await user.paste(text);
}
const searchBox = () =>
  screen.getByRole("searchbox", { name: "Tìm tài khoản" });

beforeEach(() => resetAccountMockStore());

describe("AccountManagement", () => {
  it("hiện bảng với cột đúng spec, nhân viên dùng tên đăng nhập, bệnh nhân dùng email", async () => {
    renderScreen();
    await loaded();
    expect(screen.getAllByText(/@mail\.vn$/).length).toBeGreaterThan(0);
    for (const name of [
      "Định danh",
      "Họ tên",
      "Loại tài khoản",
      "Trạng thái",
      "Ngày tạo",
    ])
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Tạo tài khoản" }),
    ).toHaveLength(1);
    expect(screen.getByText(/Trang 1\/3/)).toBeInTheDocument();
  });

  it("trạng thái trống khi chưa có tài khoản và khi bộ lọc không khớp", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();

    await search(user, "khong-co-ai");
    expect(
      await screen.findByText(
        "Không có tài khoản nào khớp bộ lọc. Bỏ bớt bộ lọc hoặc thử từ khóa khác.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
  });

  it("trạng thái trống khi chưa có dữ liệu dạy cách tạo tài khoản", async () => {
    accountStore().splice(0);
    renderScreen();
    expect(
      await screen.findByText(
        "Chưa có tài khoản nào. Bấm Tạo tài khoản để cấp tài khoản cho nhân viên phòng khám.",
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải (từ khóa quá dài) hiện thông báo và nút Thử lại", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();

    await search(user, "a".repeat(101));
    expect(
      await screen.findAllByText(
        "Tiêu chí lọc chưa hợp lệ. Vui lòng kiểm tra lại.",
        undefined,
        SLOW,
      ),
    ).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("đổi bộ lọc thì về trang đầu", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();

    await user.click(screen.getByRole("button", { name: "Sau" }));
    expect(
      await screen.findByText(/Trang 2\/3/, undefined, SLOW),
    ).toBeInTheDocument();

    await user.selectOptions(
      screen.getByRole("combobox", { name: "Loại tài khoản" }),
      "PATIENT",
    );
    expect(
      await screen.findByText(/Trang 1\/2/, undefined, SLOW),
    ).toBeInTheDocument();
  });

  it("menu dòng theo allowedActions; Vô hiệu hóa -> xác nhận -> dòng đổi trạng thái", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await search(user, "letan.an");
    expect(
      await screen.findByText(/Trang 1\/1/, undefined, SLOW),
    ).toBeInTheDocument();

    const row = screen.getByRole("row", { name: /letan\.an/ });
    expect(within(row).getByText("Đang hoạt động")).toBeInTheDocument();
    await user.click(
      within(row).getByRole("button", { name: "Thao tác cho letan.an" }),
    );
    expect(
      screen.queryByRole("menuitem", { name: "Kích hoạt" }),
    ).not.toBeInTheDocument();
    await user.click(
      await screen.findByRole("menuitem", { name: "Vô hiệu hóa" }),
    );

    expect(
      await screen.findByText("Vô hiệu hóa tài khoản?"),
    ).toBeInTheDocument();
    const confirm = screen
      .getAllByRole("button", { name: "Vô hiệu hóa" })
      .at(-1)!;
    await user.click(confirm);

    expect(
      await screen.findByText(
        "Trạng thái tài khoản đã được cập nhật thành Đã vô hiệu hóa.",
      ),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(screen.getByRole("row", { name: /letan\.an/ })).getByText(
          "Đã vô hiệu hóa",
        ),
      ).toBeInTheDocument(),
    );

    // Giờ chỉ còn Kích hoạt.
    await user.click(
      screen.getByRole("button", { name: "Thao tác cho letan.an" }),
    );
    expect(
      await screen.findByRole("menuitem", { name: "Kích hoạt" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Vô hiệu hóa" }),
    ).not.toBeInTheDocument();
  });

  it("mở chi tiết ở Sheet khi bấm Xem", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await search(user, "letan.an");
    expect(
      await screen.findByText(/Trang 1\/1/, undefined, SLOW),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Xem tài khoản letan.an" }),
    );

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(await screen.findByText("Liên hệ công việc")).toBeInTheDocument();
    expect(screen.getByText("0901 234 567")).toBeInTheDocument();
  });
});
