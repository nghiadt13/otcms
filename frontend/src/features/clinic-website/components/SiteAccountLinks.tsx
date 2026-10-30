"use client";

import { ChevronDown, CircleUser, LogIn, LogOut, UserPlus } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { signOut } from "@/lib/auth/authSession";
import type { CurrentAccount } from "@/lib/auth/currentAccountApi";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { clearMockSignedInKind } from "@/lib/session/mockSignedInKind";

const topLinkClass =
  "inline-flex items-center gap-1.5 rounded-sm text-white transition-colors duration-150 hover:text-brand-muted focus-visible:outline-2 focus-visible:outline-white motion-reduce:transition-none";

// Bản cho hàng menu trắng: chữ đậm trên nền sáng.
const navLinkClass =
  "inline-flex items-center gap-1.5 rounded-sm font-medium text-foreground/80 transition-colors duration-150 hover:text-primary focus-visible:ring-3 focus-visible:ring-ring/50 outline-none motion-reduce:transition-none";

// Lối vào chung theo vai trò (RoleHomeRedirect), nên không cần biết khu nào ở đây.
const WORKSPACE_HREF = "/workspace";

// "Cá nhân": bệnh nhân vào thẳng trang chỉnh sửa thông tin; vai trò khác về khu làm việc của mình.
function personalHref(account: CurrentAccount): string {
  return account.accountType === "PATIENT"
    ? PATIENT_ROUTES.account
    : WORKSPACE_HREF;
}

/**
 * Cụm tài khoản ở header công khai. Chưa đăng nhập: "Đăng nhập" và "Đăng ký". Đã đăng nhập (tên
 * lấy từ backend): biểu tượng người dùng kèm tên, bấm mở menu vào khu làm việc hoặc đăng xuất.
 * `nav` nằm ở hàng menu trắng (chữ tối); `topbar` là dải trên cùng (chữ trắng, nền đậm); `sheet`
 * là menu di động (nút đầy hàng).
 */
export function SiteAccountLinks({
  variant,
  onNavigate,
}: {
  variant: "nav" | "topbar" | "sheet";
  /** Đóng menu di động sau khi bấm một mục. */
  onNavigate?: () => void;
}) {
  const state = useCurrentAccount();
  const linkClass = variant === "nav" ? navLinkClass : topLinkClass;

  async function handleSignOut() {
    // `signOut` bỏ qua lỗi mạng và luôn xoá phiên phía trình duyệt.
    await signOut();
    clearMockSignedInKind();
    state.signOutLocally();
    onNavigate?.();
  }

  if (state.status === "signedIn") {
    return variant !== "sheet" ? (
      <AccountDropdown
        account={state.account}
        onSignOut={handleSignOut}
        variant={variant}
      />
    ) : (
      <AccountStack
        account={state.account}
        onNavigate={onNavigate}
        onSignOut={handleSignOut}
      />
    );
  }

  // `loading` hiện như chưa đăng nhập (an toàn): người đã có phiên thấy tên sau lần đọc đầu.
  return variant !== "sheet" ? (
    <>
      <Link className={linkClass} href="/login">
        <LogIn aria-hidden className="size-4" />
        Đăng nhập
      </Link>
      <Link className={linkClass} href="/register">
        <UserPlus aria-hidden className="size-4" />
        Đăng ký
      </Link>
    </>
  ) : (
    <>
      <Link
        className={publicButtonClass("secondary")}
        href="/login"
        onClick={onNavigate}
      >
        <LogIn aria-hidden />
        Đăng nhập
      </Link>
      <Link
        className={publicButtonClass("secondary")}
        href="/register"
        onClick={onNavigate}
      >
        <UserPlus aria-hidden />
        Đăng ký
      </Link>
    </>
  );
}

function AccountDropdown({
  account,
  onSignOut,
  variant,
}: {
  account: CurrentAccount;
  onSignOut: () => void;
  variant: "nav" | "topbar";
}) {
  const nav = variant === "nav";
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Tài khoản: ${account.displayName}`}
        className={
          nav
            ? "group inline-flex h-11 max-w-64 cursor-pointer items-center gap-2.5 rounded-full border border-input bg-card py-1 pr-3.5 pl-1 text-base font-medium text-foreground transition-colors duration-150 outline-none hover:border-primary hover:text-primary focus-visible:ring-3 focus-visible:ring-ring/50 data-popup-open:border-primary motion-reduce:transition-none"
            : `${topLinkClass} max-w-56 cursor-pointer outline-none`
        }
      >
        {nav ? (
          <>
            <span
              aria-hidden
              className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-base font-semibold text-primary-foreground uppercase"
            >
              {account.displayName.trim().charAt(0) || "?"}
            </span>
            <span className="truncate">{account.displayName}</span>
            <ChevronDown
              aria-hidden
              className="size-4 shrink-0 text-muted-foreground transition-transform duration-150 group-data-popup-open:rotate-180 motion-reduce:transition-none"
            />
          </>
        ) : (
          <>
            <CircleUser aria-hidden className="size-5 shrink-0" />
            <span className="truncate font-medium">{account.displayName}</span>
          </>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        className="min-w-56"
        sideOffset={nav ? 8 : 4}
      >
        <DropdownMenuItem
          className="h-11 text-base"
          render={<Link href={personalHref(account)} />}
        >
          <CircleUser aria-hidden />
          Cá nhân
        </DropdownMenuItem>
        <DropdownMenuItem className="h-11 text-base" onClick={onSignOut}>
          <LogOut aria-hidden />
          Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function AccountStack({
  account,
  onNavigate,
  onSignOut,
}: {
  account: CurrentAccount;
  onNavigate?: () => void;
  onSignOut: () => void;
}) {
  return (
    <>
      <p className="flex items-center gap-2 text-base font-semibold">
        <CircleUser aria-hidden className="size-5 shrink-0 text-primary" />
        <span className="truncate">{account.displayName}</span>
      </p>
      <Link
        className={publicButtonClass("secondary")}
        href={personalHref(account)}
        onClick={onNavigate}
      >
        Cá nhân
      </Link>
      <button
        className={publicButtonClass("secondary")}
        onClick={onSignOut}
        type="button"
      >
        <LogOut aria-hidden />
        Đăng xuất
      </button>
    </>
  );
}
