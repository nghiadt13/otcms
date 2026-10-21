import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SidebarProvider } from "@/components/ui/sidebar";
import { resetMyAccountMockStore } from "@/features/my-account/api/myAccountApi";
import { AdminAccountMenu } from "./AdminAccountMenu";

const replace = vi.fn();
const signOut = vi.fn().mockResolvedValue({ error: null });

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  usePathname: () => "/admin",
}));

// Ranh giới ngoài: client Supabase.
vi.mock("@/lib/supabase/browser", () => ({
  getSupabaseBrowserClient: () => ({ auth: { signOut } }),
}));

beforeEach(() => {
  resetMyAccountMockStore();
  replace.mockClear();
  signOut.mockClear();
  window.matchMedia ??= ((query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia;
});

function setup() {
  render(
    <SidebarProvider>
      <AdminAccountMenu />
    </SidebarProvider>,
  );
}

async function openMenu() {
  await userEvent.click(await screen.findByRole("button", { name: /admin\.hoa/ }));
}

describe("AdminAccountMenu", () => {
  it("hiện tên đăng nhập và vai trò, không hiện email", async () => {
    setup();
    expect(await screen.findByText("admin.hoa")).toBeInTheDocument();
    expect(screen.getByText("Quản trị viên")).toBeInTheDocument();
    expect(screen.queryByText(/@/)).not.toBeInTheDocument();
  });

  it("menu có Hồ sơ tài khoản, Đổi mật khẩu và Đăng xuất", async () => {
    setup();
    await openMenu();
    expect(
      await screen.findByRole("menuitem", { name: /Hồ sơ tài khoản/ }),
    ).toHaveAttribute("href", "/admin/account");
    expect(screen.getByRole("menuitem", { name: /Đổi mật khẩu/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Đăng xuất/ })).toBeInTheDocument();
  });

  it("Đổi mật khẩu mở dialog và dialog còn đó sau khi menu đóng", async () => {
    setup();
    await openMenu();
    await userEvent.click(await screen.findByRole("menuitem", { name: /Đổi mật khẩu/ }));
    expect(await screen.findByRole("dialog", { name: "Đổi mật khẩu" })).toBeInTheDocument();
    expect(screen.getByLabelText("Mật khẩu hiện tại")).toBeInTheDocument();
  });

  it("Đăng xuất xóa phiên nhân viên rồi replace về /staff-login", async () => {
    window.sessionStorage.setItem("otcms.staff.session", "{} ");
    setup();
    await openMenu();
    await userEvent.click(await screen.findByRole("menuitem", { name: /Đăng xuất/ }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/staff-login"));
    expect(window.sessionStorage.getItem("otcms.staff.session")).toBeNull();
    expect(signOut).not.toHaveBeenCalled();
  });
});
