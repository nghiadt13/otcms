"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/client";
import { isApiError } from "@/lib/api/apiError";
import {
  AuthSessionError,
  clearLocalAuthSession,
  getSession,
} from "@/lib/auth/authSession";
import { APP_MESSAGES } from "@/lib/messages/appMessages";

/** Recheck each destination, including navigation within the persistent Admin layout. */
export function AdminAccess({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return <AdminAccessCheck key={pathname}>{children}</AdminAccessCheck>;
}

function AdminAccessCheck({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<
    "loading" | "allowed" | "denied" | "error"
  >("loading");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let generation = 0;
    let controller: AbortController | undefined;
    const check = async () => {
      const request = ++generation;
      controller?.abort();
      controller = new AbortController();
      const signal = controller.signal;
      // Revalidate on focus without unmounting an in-progress form or file upload.
      await Promise.resolve();
      if (signal.aborted) return;
      setState((previous) => (previous === "allowed" ? previous : "loading"));
      try {
        const session = await getSession();
        if (request !== generation) return;
        if (!session) {
          setState("loading");
          router.replace("/staff-login");
          return;
        }
        if (session.accountType !== "ADMIN") {
          setState("denied");
          return;
        }
        if (session.mustChangePassword) {
          setState("loading");
          router.replace("/first-login-password");
          return;
        }
        // Validate the current account against Spring, not just a cached JWT role.
        const account = await apiRequest<{
          role: string;
          status: string;
          mustChangePassword: boolean;
        }>("/api/v1/me", { signal });
        if (request !== generation) return;
        if (account.role !== "ADMIN" || account.status !== "ACTIVE") {
          setState("denied");
        } else if (account.mustChangePassword) {
          setState("loading");
          router.replace("/first-login-password");
        } else {
          setState("allowed");
        }
      } catch (error) {
        if (request !== generation) return;
        if (
          (isApiError(error) || error instanceof AuthSessionError) &&
          error.status === 401
        ) {
          clearLocalAuthSession();
          setState("loading");
          router.replace("/staff-login");
        } else {
          setState(
            isApiError(error) && error.status === 403 ? "denied" : "error",
          );
        }
      }
    };
    const onStorage = (event: StorageEvent) => {
      // Another tab may have signed out while this tab still holds an access token.
      if (event.key === "otcms.auth.hasSession" || event.key === null) {
        if (event.newValue === null) {
          clearLocalAuthSession();
          setState("loading");
        }
        void check();
      }
    };
    void check();
    window.addEventListener("focus", check);
    window.addEventListener("pageshow", check);
    window.addEventListener("storage", onStorage);
    return () => {
      generation++;
      controller?.abort();
      window.removeEventListener("focus", check);
      window.removeEventListener("pageshow", check);
      window.removeEventListener("storage", onStorage);
    };
  }, [attempt, router]);

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
        {state === "error" ? APP_MESSAGES["MSG-04"] : APP_MESSAGES["MSG-14"]}
      </p>
      {state === "error" ? (
        <Button variant="outline" onClick={() => setAttempt((n) => n + 1)}>
          Thử lại
        </Button>
      ) : (
        <Button
          variant="outline"
          onClick={() => router.replace("/staff-login")}
        >
          Đăng nhập nhân viên
        </Button>
      )}
    </div>
  );
}
