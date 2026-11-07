import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearFakeAccounts,
  resetFakeAccountBackend,
} from "@/test/accountAdminFakeBackend";
import { AccountManagement } from "./AccountManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

// Mock ở ranh giới HTTP: backend giả trả đúng JSON của BE (KAN-27 cho nhân viên).
vi.mock("@/lib/api/client", async (importOriginal) => {
  const fake = await import("@/test/accountAdminFakeBackend");
  return {
    ...(await importOriginal<typeof import("@/lib/api/client")>()),
    apiRequest: vi.fn(fake.fakeAccountApiRequest),
  };
});

const renderScreen = () =>
  render(
    <>
      <Toaster />
      <AccountManagement />
    </>,
  );

// Debounce 250 ms của ô tìm kiếm: để dư thời gian cho máy chậm.
const SLOW = { timeout: 4000 };
const loaded = () => screen.findByText(/Trang 1\/3/, undefined, SLOW);
const searchBox = () =>
  screen.getByRole("searchbox", { name: "Tìm tài khoản" });
async function search(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.click(searchBox());
  await user.paste(text);
}
async function openStaffTab(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("tab", { name: "Nhân viên" }));
  return screen.findByRole("row", { name: /letan\.an/ }, SLOW);
}

beforeEach(() => resetFakeAccountBackend());

describe("AccountManagement", () => {
  it("mở ở tab Bệnh nhân: định danh là email, chưa có nút Tạo tài khoản", async () => {
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
      screen.queryByRole("button", { name: "Tạo tài khoản" }),
    ).not.toBeInTheDocument();
  });

  it("tab Nhân viên hiện dữ liệu KAN-27 (không lỗi): tên đăng nhập, họ tên từ hồ sơ, có nút Tạo tài khoản", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    const row = await openStaffTab(user);

    expect(within(row).getByText("Nguyễn Thị An")).toBeInTheDocument();
    expect(within(row).getByText("Đang hoạt động")).toBeInTheDocument();
    expect(
      screen.getByRole("row", { name: /kttd\.cuong/ }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Đã có lỗi xảy ra/)).not.toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Tạo tài khoản" }),
    ).toHaveLength(1);
  });

  it("trạng thái trống: bộ lọc không khớp", async () => {
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

  it("trạng thái trống: chưa có nhân viên nào dạy cách tạo tài khoản", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    clearFakeAccounts();
    await user.click(screen.getByRole("tab", { name: "Nhân viên" }));
    expect(
      await screen.findByText(
        "Chưa có tài khoản nào. Bấm Tạo tài khoản để cấp tài khoản cho nhân viên phòng khám.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải (từ khóa quá dài) hiện thông báo và nút Thử lại", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();

    await search(user, "a".repeat(101));
    expect(
      await screen.findAllByText(formatAppMessage("MSG-54"), undefined, SLOW),
    ).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("đổi bộ lọc trạng thái thì về trang đầu", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();

    await user.click(screen.getByRole("button", { name: "Sau" }));
    expect(
      await screen.findByText(/Trang 2\/3/, undefined, SLOW),
    ).toBeInTheDocument();

    await user.click(
      within(screen.getByLabelText("Trạng thái")).getByText("Đã vô hiệu hóa"),
    );
    expect(
      await screen.findByText(/Trang 1\/1/, undefined, SLOW),
    ).toBeInTheDocument();
  });

  it("nhân viên: menu dòng theo allowedActions; Vô hiệu hóa -> xác nhận -> dòng đổi trạng thái", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    const row = await openStaffTab(user);

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
    await user.click(
      screen.getAllByRole("button", { name: "Vô hiệu hóa" }).at(-1)!,
    );

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

  it("nhân viên: mở chi tiết ở Sheet khi bấm Xem, hiện hồ sơ", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await openStaffTab(user);
    await user.click(
      screen.getByRole("button", { name: "Xem tài khoản letan.an" }),
    );

    const sheet = await screen.findByRole("dialog");
    expect(
      (await within(sheet).findAllByText("Nguyễn Thị An")).length,
    ).toBeGreaterThan(0);
    expect(within(sheet).getByText("letan.an")).toBeInTheDocument();
  });

  it("bệnh nhân: đổi trạng thái từ dòng bảng gọi endpoint bệnh nhân", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await search(user, "lan.tran@");
    // Wait for the debounced search to replace the initial page before opening its row menu.
    await screen.findByText(/Trang 1\/1/, undefined, SLOW);
    const row = await screen.findByRole(
      "row",
      { name: /lan\.tran@mail\.vn/ },
      SLOW,
    );
    await user.click(
      within(row).getByRole("button", {
        name: "Thao tác cho lan.tran@mail.vn",
      }),
    );
    await user.click(
      await screen.findByRole("menuitem", { name: "Vô hiệu hóa" }),
    );
    await user.click(
      screen.getAllByRole("button", { name: "Vô hiệu hóa" }).at(-1)!,
    );
    expect(
      (
        await screen.findAllByText(
          "Trạng thái tài khoản đã được cập nhật thành Đã vô hiệu hóa.",
        )
      ).length,
    ).toBeGreaterThan(0);
    await waitFor(() =>
      expect(
        within(screen.getByRole("row", { name: /lan.tran@mail.vn/ })).getByText(
          "Đã vô hiệu hóa",
        ),
      ).toBeInTheDocument(),
    );
  });
});
