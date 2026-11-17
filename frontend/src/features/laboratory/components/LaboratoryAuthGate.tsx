"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getSession } from "@/lib/auth/authSession";
import { formatAppMessage } from "@/lib/messages/appMessages";

// This gate controls the preview UI; backend APIs must enforce authorization independently.
export function LaboratoryAuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<"checking" | "ready" | "error">(
    "checking",
  );
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    getSession()
      .then((session) => {
        if (!active) return;
        if (!session) router.replace("/staff-login");
        else if (session.accountType !== "LAB_TECHNICIAN")
          router.replace("/workspace");
        else if (session.mustChangePassword)
          router.replace("/first-login-password");
        else setState("ready");
      })
      .catch(() => {
        if (active) setState("error");
      });
    return () => {
      active = false;
    };
  }, [attempt, router]);

  if (state === "ready") return <>{children}</>;
  if (state === "error")
    return (
      <section className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-16">
        <p role="alert">{formatAppMessage("MSG-04")}</p>
        <Button
          variant="outline"
          onClick={() => {
            setState("checking");
            setAttempt((value) => value + 1);
          }}
        >
          Thử lại
        </Button>
      </section>
    );
  return (
    <div
      className="space-y-4 p-6"
      role="status"
      aria-label="Đang kiểm tra đăng nhập"
    >
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}
