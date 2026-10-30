"use client";

import { useEffect } from "react";
import { PublicErrorState } from "@/components/brand/PublicErrorState";
import { publicContainerClass } from "@/components/brand/publicLayout";

// Lỗi trong các trang giới thiệu. Nằm trong `(site)/layout.tsx` nên header và footer vẫn hiện.
export default function SiteError({
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
    <div
      className={`${publicContainerClass} flex flex-col items-center py-24 lg:py-32`}
    >
      <PublicErrorState retry={retry} />
    </div>
  );
}
