"use client";

import { useEffect } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";
import { authMessages } from "../authMessages";

// Vừa xác minh email xong ở /register (UC06): hiện toast MSG-10 một lần rồi xoá
// `registered` khỏi URL để tải lại trang không báo lại. Khu public chưa có Toaster
// nên gắn luôn ở đây. `id` cố định để Strict Mode (effect chạy 2 lần) không hiện 2 toast.
export function RegisteredToast() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  useEffect(() => {
    if (searchParams.get("registered") !== "1") return;
    toast.success(authMessages["MSG-10"], { id: "registered" });
    const rest = new URLSearchParams(searchParams.toString());
    rest.delete("registered");
    const query = rest.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
  }, [searchParams, pathname, router]);

  return <Toaster position="top-center" />;
}
