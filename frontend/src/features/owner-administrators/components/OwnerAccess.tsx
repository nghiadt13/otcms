"use client";
import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { getSession } from "@/lib/auth/authSession";
import { Button } from "@/components/ui/button";

export function OwnerAccess({ children }: { children: ReactNode }) {
  const [state, setState] = useState("loading");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    getSession()
      .then((session) => {
        if (active)
          setState(session?.accountType === "OWNER" ? "allowed" : "denied");
      })
      .catch(() => {
        if (active) setState("error");
      });
    return () => {
      active = false;
    };
  }, [attempt]);
  if (state === "allowed") return children;
  if (state === "loading")
    return (
      <p role="status" className="p-6 text-sm text-muted-foreground">
        Đang kiểm tra phiên đăng nhập…
      </p>
    );
  return (
    <div className="space-y-4 p-6">
      <p role="alert">
        {state === "error"
          ? "Không thể kiểm tra phiên đăng nhập."
          : "Khu vực này chỉ dành cho Chủ phòng khám."}
      </p>
      {state === "error" && (
        <Button
          variant="outline"
          onClick={() => {
            setState("loading");
            setAttempt((value) => value + 1);
          }}
        >
          Thử lại
        </Button>
      )}
      <Link
        className="block text-primary underline"
        href="/staff-login?next=/owner/accounts"
      >
        Đăng nhập nhân viên
      </Link>
    </div>
  );
}
