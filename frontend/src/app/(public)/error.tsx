"use client";

import { useEffect } from "react";
import { PublicErrorState } from "@/components/brand/PublicErrorState";

// Lỗi runtime trong khu công khai (kể cả màn xác thực). Trang trong nhóm `(site)` có
// `(site)/error.tsx` riêng để giữ header và footer.
export default function PublicError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    // Chỗ gắn dịch vụ ghi log lỗi khi có; tạm thời ghi ra console.
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center bg-background px-6 py-16 text-foreground">
      <PublicErrorState retry={retry} />
    </main>
  );
}
