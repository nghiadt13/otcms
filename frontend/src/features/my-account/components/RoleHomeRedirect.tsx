"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { fetchCurrentAccount } from "@/lib/auth/currentAccountApi";
import { messageFromError } from "@/lib/messages/appMessages";
import { homeRouteForAccount } from "../homeRouteForAccount";

// Lối vào chung `/workspace`: chưa có phiên (hoặc backend trả 401) thì về đăng nhập; có phiên thì
// đọc tài khoản từ backend (`GET /api/v1/me`) và chuyển tới khu đúng vai trò. Lỗi khác hiện thông
// báo kèm nút thử lại.
export function RoleHomeRedirect() {
  const router = useRouter();
  const [attempt, setAttempt] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function routeToHome() {
      const account = await fetchCurrentAccount();
      if (!active) return;
      router.replace(
        account ? homeRouteForAccount(account.accountType) : "/login",
      );
    }

    routeToHome().catch((cause: unknown) => {
      if (!active) return;
      if (isApiError(cause) && cause.status === 401) router.replace("/login");
      else setFailure(messageFromError(cause));
    });
    return () => {
      active = false;
    };
  }, [router, attempt]);

  function retry() {
    setFailure(null);
    setAttempt((count) => count + 1);
  }

  if (failure) {
    return (
      <section className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-16 text-center">
        <h1 className="sr-only">Khu làm việc của bạn</h1>
        <p role="alert" className="text-base text-destructive">
          {failure}
        </p>
        <div className="flex flex-wrap justify-center gap-3">
          <Button variant="outline" onClick={retry}>
            Thử lại
          </Button>
          <Link
            className="rounded-sm text-base font-semibold text-primary underline-offset-4 hover:underline"
            href="/login"
          >
            Đăng nhập lại
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section
      aria-busy="true"
      aria-live="polite"
      className="mx-auto grid max-w-md gap-3 px-4 py-16"
    >
      <h1 className="sr-only">Khu làm việc của bạn</h1>
      <span className="sr-only">Đang mở khu làm việc của bạn</span>
      <Skeleton className="h-6 w-2/3" />
      <Skeleton className="h-12 w-full" />
    </section>
  );
}
