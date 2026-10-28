import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PATIENT_NAV_ITEMS, groupPatientNavItems } from "../patientNavigation";
import { PatientPortalHeader } from "./PatientPortalHeader";

const replace = vi.fn();
const signOut = vi.hoisted(() => vi.fn());
let pathname = "/portal";

// Chỉ giả đường dẫn và router của Next; header, menu và Sheet chạy như thật.
vi.mock("next/navigation", () => ({
  usePathname: () => pathname,
  useRouter: () => ({ replace, push: vi.fn() }),
}));

// Ranh giới ngoài: lớp phiên đăng nhập và `GET /api/v1/me` gọi backend. Tên lấy từ tài khoản
// backend (đã nhớ trong tab nên header hiện ngay, không qua khung chờ).
const account = {
  id: "11111111-1111-1111-1111-111111111111",
  accountType: "PATIENT",
  email: "an@example.com",
  displayName: "Nguyễn Văn An",
};
vi.mock("@/lib/auth/authSession", () => ({ signOut }));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  fetchCurrentAccount: async () => account,
  peekCurrentAccount: () => account,
}));

beforeEach(() => {
  pathname = "/portal";
  replace.mockClear();
  signOut.mockClear();
  signOut.mockResolvedValue(undefined);
});

describe("PatientPortalHeader", () => {
  it("có logo về trang chủ, nút Đặt lịch khám và tên người đang đăng nhập", () => {
    render(<PatientPortalHeader />);
    expect(screen.getByRole("link", { name: "OTCMS" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveAttribute(
      "href",
      "/portal/appointments/new",
    );
    expect(
      screen.getByRole("button", { name: "Tài khoản: Nguyễn Văn An" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn An")).toBeInTheDocument();
  });

  it("ẩn nút Đặt lịch khám khi đang ở trang đặt lịch", () => {
    pathname = "/portal/appointments/new";
    render(<PatientPortalHeader />);
    expect(
      screen.queryByRole("link", { name: "Đặt lịch khám" }),
    ).not.toBeInTheDocument();
  });

  it("trang chủ và chi tiết lượt khám: nút Đặt lịch khám là outline, vì trang đã có nút chính riêng", () => {
    for (const home of ["/portal", "/portal/visits/vis-an-today"]) {
      pathname = home;
      const { unmount } = render(<PatientPortalHeader />);
      expect(
        screen.getByRole("link", { name: "Đặt lịch khám" }),
      ).not.toHaveClass("bg-primary");
      unmount();
    }
  });

  it("form viết đánh giá và trang thông báo: nút Đặt lịch khám là outline, nút chính là của trang", () => {
    for (const page of [
      "/portal/reviews/new?visitId=vis-an-done",
      "/portal/notifications",
    ]) {
      pathname = page.split("?")[0];
      const { unmount } = render(<PatientPortalHeader />);
      expect(
        screen.getByRole("link", { name: "Đặt lịch khám" }),
      ).not.toHaveClass("bg-primary");
      unmount();
    }
  });

  it("các trang khác (kể cả danh sách lượt khám): nút Đặt lịch khám là nút chính", () => {
    pathname = "/portal/visits";
    render(<PatientPortalHeader />);
    expect(screen.getByRole("link", { name: "Đặt lịch khám" })).toHaveClass(
      "bg-primary",
    );
  });

  it("chuông thông báo đi qua slot, đứng trước ô tài khoản", () => {
    render(
      <PatientPortalHeader
        notificationSlot={
          <a aria-label="Thông báo, 3 chưa đọc" href="/portal/notifications">
            chuông
          </a>
        }
      />,
    );
    const bell = screen.getByRole("link", { name: "Thông báo, 3 chưa đọc" });
    const account = screen.getByRole("button", {
      name: "Tài khoản: Nguyễn Văn An",
    });
    expect(
      bell.compareDocumentPosition(account) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("không truyền slot thì header vẫn đủ nút, không có chuông", () => {
    render(<PatientPortalHeader />);
    expect(
      screen.queryByRole("link", { name: /Thông báo/ }),
    ).not.toBeInTheDocument();
  });

  it("nút Menu chỉ hiện khi đã có mục menu (ẩn mục chưa có)", () => {
    render(<PatientPortalHeader />);
    const hasItems = groupPatientNavItems(PATIENT_NAV_ITEMS).length > 0;
    expect(screen.queryByRole("button", { name: "Menu" }) !== null).toBe(
      hasItems,
    );
  });

  it("menu tài khoản có vai trò Bệnh nhân và Đăng xuất về /login", async () => {
    render(<PatientPortalHeader />);
    await userEvent.click(
      screen.getByRole("button", { name: "Tài khoản: Nguyễn Văn An" }),
    );
    expect(await screen.findByText("Bệnh nhân")).toBeInTheDocument();

    await userEvent.click(
      await screen.findByRole("menuitem", { name: /Đăng xuất/ }),
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(signOut).toHaveBeenCalledTimes(1);
  });
});
