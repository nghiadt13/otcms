import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetStaffProfileMockStore } from "../api/staffProfileMockData";
import { StaffProfileManagement } from "./StaffProfileManagement";

const push = vi.fn();
const replace = vi.fn();
let search = "";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace }),
  usePathname: () => "/admin/staff-profiles",
  useSearchParams: () => new URLSearchParams(search),
}));

const toastError = vi.fn();
const toastSuccess = vi.fn();
vi.mock("sonner", () => ({
  toast: {
    error: (...args: unknown[]) => toastError(...args),
    success: (...args: unknown[]) => toastSuccess(...args),
    warning: vi.fn(),
  },
}));

const TIMEOUT = { timeout: 3000 };

beforeEach(() => {
  resetStaffProfileMockStore();
  push.mockClear();
  replace.mockClear();
  toastError.mockClear();
  toastSuccess.mockClear();
  search = "";
  URL.createObjectURL = vi.fn(() => "blob:preview");
  URL.revokeObjectURL = vi.fn();
});

async function searchFor(text: string) {
  await userEvent.type(
    screen.getByRole("searchbox", { name: "Tìm hồ sơ nhân viên" }),
    text,
  );
}

describe("StaffProfileManagement", () => {
  it("hiện cột chính và dấu — cho vai trò không phải bác sĩ", async () => {
    render(<StaffProfileManagement />);
    await screen.findByText("BS. Lê Minh Quân", {}, TIMEOUT);
    await searchFor("le.tan.hoa");
    const row = (await screen.findByText("Trần Thị Hoa", {}, TIMEOUT)).closest(
      "tr",
    )!;
    expect(within(row).getByText("Lễ tân")).toBeInTheDocument();
    expect(within(row).getByText("le.tan.hoa")).toBeInTheDocument();
    expect(within(row).getByLabelText("Không áp dụng")).toHaveTextContent("—");
    expect(within(row).getByText("Đang hoạt động")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Xóa/ }),
    ).not.toBeInTheDocument();
  });

  it("tìm không thấy thì dạy bỏ bớt bộ lọc", async () => {
    render(<StaffProfileManagement />);
    await screen.findByText("BS. Lê Minh Quân", {}, TIMEOUT);
    await searchFor("zzz");
    expect(
      await screen.findByText(/Không có hồ sơ khớp bộ lọc/, {}, TIMEOUT),
    ).toBeInTheDocument();
  });

  it("?accountId= mở form tạo với tài khoản chọn sẵn và ẩn nhóm bác sĩ cho lễ tân", async () => {
    search = "accountId=00000000-0000-4000-8000-000000000500"; // le.tan.minh
    render(<StaffProfileManagement />);
    const dialog = await screen.findByRole("dialog", {}, TIMEOUT);
    expect(
      await within(dialog).findByText(
        /Lê Minh Tân \(le\.tan\.minh\)/,
        {},
        TIMEOUT,
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("Vai trò: Lễ tân")).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Họ tên hiển thị/)).toHaveValue(
      "Lê Minh Tân",
    );
    expect(within(dialog).queryByText("Chuyên khoa")).not.toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith("/admin/staff-profiles");
  });

  it("?accountId= không đủ điều kiện thì báo MSG-23 và chưa chọn tài khoản", async () => {
    search = "accountId=khong-du-dieu-kien";
    render(<StaffProfileManagement />);
    const dialog = await screen.findByRole("dialog", {}, TIMEOUT);
    await within(dialog).findByRole("combobox", {}, TIMEOUT);
    expect(toastError).toHaveBeenCalledWith(
      expect.stringContaining("không đủ điều kiện"),
      expect.anything(),
    );
    expect(within(dialog).getByRole("combobox")).toHaveTextContent(
      "Chọn tài khoản",
    );
    expect(
      within(dialog).getByRole("button", { name: "Lưu hồ sơ" }),
    ).toBeDisabled();
  });

  it("tạo hồ sơ lễ tân: lỗi từng ô khi để trống, rồi lưu thành công", async () => {
    search = "accountId=00000000-0000-4000-8000-000000000501"; // le.tan.lan
    render(<StaffProfileManagement />);
    const dialog = await screen.findByRole("dialog", {}, TIMEOUT);
    await within(dialog).findByText(/Đỗ Thị Lan/, {}, TIMEOUT);
    const nameInput = within(dialog).getByLabelText(/Họ tên hiển thị/);
    await userEvent.clear(nameInput);
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Lưu hồ sơ" }),
    );
    expect(await within(dialog).findAllByRole("alert")).toHaveLength(2);
    expect(nameInput).toHaveFocus();

    await userEvent.type(nameInput, "Đỗ Thị Lan");
    await userEvent.type(
      within(dialog).getByLabelText(/Liên hệ công việc/),
      "lan@example.com",
    );
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Lưu hồ sơ" }),
    );
    expect(
      await within(dialog).findByRole("button", { name: "Đang lưu..." }),
    ).toBeDisabled();
    await vi.waitFor(() => expect(toastSuccess).toHaveBeenCalled(), TIMEOUT);
    await vi.waitFor(
      () => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
      TIMEOUT,
    );
  });

  it(
    "gỡ bác sĩ còn lịch hẹn: toast MSG-36 có nút Xem lịch hẹn, hồ sơ giữ nguyên",
    { timeout: 20000 },
    async () => {
      render(<StaffProfileManagement />);
      await screen.findByText("BS. Lê Minh Quân", {}, TIMEOUT);
      await searchFor("bs.trung");
      await userEvent.click(
        await screen.findByRole(
          "button",
          { name: "BS. Nguyễn Văn Trung" },
          TIMEOUT,
        ),
      );

      const sheet = await screen.findByRole("dialog", {}, TIMEOUT);
      await userEvent.click(
        await within(sheet).findByRole("button", { name: "Gỡ hồ sơ" }, TIMEOUT),
      );
      const confirm = await screen.findByRole("alertdialog");
      expect(confirm).toHaveTextContent(
        "Tài khoản, lịch hẹn và bệnh án được giữ.",
      );
      await userEvent.click(
        within(confirm).getByRole("button", { name: "Gỡ hồ sơ" }),
      );

      await vi.waitFor(() => expect(toastError).toHaveBeenCalled(), TIMEOUT);
      const [message, options] = toastError.mock.calls[0] as [
        string,
        { action: { label: string; onClick: () => void } },
      ];
      expect(message).toContain("lịch hẹn");
      expect(options.action.label).toBe("Xem lịch hẹn");
      options.action.onClick();
      expect(push).toHaveBeenCalledWith(
        expect.stringMatching(/^\/admin\/appointments\?doctorId=.+/),
      );
      expect(toastSuccess).not.toHaveBeenCalled();
    },
  );

  it(
    "hồ sơ đã gỡ chỉ xem: không có Sửa, Gỡ hồ sơ, công khai",
    { timeout: 20000 },
    async () => {
      render(<StaffProfileManagement />);
      await screen.findByText("BS. Lê Minh Quân", {}, TIMEOUT);
      await userEvent.selectOptions(screen.getByLabelText("Hồ sơ"), "REMOVED");
      await searchFor("bs.an");
      await userEvent.click(
        await screen.findByRole(
          "button",
          { name: "BS. Phạm Quốc An" },
          TIMEOUT,
        ),
      );
      const sheet = await screen.findByRole("dialog", {}, TIMEOUT);
      expect(
        (await within(sheet).findAllByText("Đã gỡ", {}, TIMEOUT)).length,
      ).toBeGreaterThan(0);
      expect(
        within(sheet).queryByRole("button", { name: "Sửa" }),
      ).not.toBeInTheDocument();
      expect(
        within(sheet).queryByRole("button", { name: "Gỡ hồ sơ" }),
      ).not.toBeInTheDocument();
      expect(within(sheet).queryByRole("switch")).not.toBeInTheDocument();
    },
  );
});
