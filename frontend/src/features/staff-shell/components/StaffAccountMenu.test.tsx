import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StaffAccountMenu } from "./StaffAccountMenu";

const replace = vi.fn();
const signOut = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  usePathname: () => "/staff",
}));

// Ranh giới ngoài: client Supabase.
vi.mock("@/lib/supabase/browser", () => ({
  getSupabaseBrowserClient: () => ({ auth: { signOut } }),
}));

beforeEach(() => {
  replace.mockClear();
  signOut.mockReset();
  signOut.mockResolvedValue({ error: null });
});

async function openMenu() {
  await userEvent.click(
    screen.getByRole("button", { name: /Trần Thu Ngân/ }),
  );
}

describe("StaffAccountMenu", () => {
  it("hiện tên và vai trò của người đăng nhập", () => {
    render(<StaffAccountMenu />);
    expect(screen.getByText("Trần Thu Ngân")).toBeInTheDocument();
    expect(screen.getByText("Lễ tân")).toBeInTheDocument();
  });

  it("menu chỉ có Đăng xuất như hành động", async () => {
    render(<StaffAccountMenu />);
    await openMenu();
    expect(
      await screen.findByRole("menuitem", { name: /Đăng xuất/ }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("menuitem")).toHaveLength(1);
  });

  it("Đăng xuất gọi Supabase rồi chuyển về /login bằng replace", async () => {
    render(<StaffAccountMenu />);
    await openMenu();
    await userEvent.click(
      await screen.findByRole("menuitem", { name: /Đăng xuất/ }),
    );
    expect(signOut).toHaveBeenCalledTimes(1);
    expect(replace).toHaveBeenCalledWith("/login");
  });

  it("lỗi khi đăng xuất vẫn chuyển về /login", async () => {
    signOut.mockRejectedValue(new Error("chưa cấu hình"));
    render(<StaffAccountMenu />);
    await openMenu();
    await userEvent.click(
      await screen.findByRole("menuitem", { name: /Đăng xuất/ }),
    );
    expect(replace).toHaveBeenCalledWith("/login");
  });
});
