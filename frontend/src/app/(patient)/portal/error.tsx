"use client";

import Link from "next/link";
import { useEffect } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";

// Lỗi runtime trong một màn khu bệnh nhân. Nằm trong portal/layout nên header vẫn hiển
// thị, người dùng có thể quay về trang chủ. Câu chữ nói rõ chuyện gì và cách khắc phục.
export default function PatientPortalError({
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
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">
        Không mở được trang này
      </h1>
      <p className="text-base text-destructive" role="alert">
        Đã có lỗi khi tải trang. Hãy thử lại; nếu vẫn lỗi, quay về trang chủ.
      </p>
      {error.digest ? (
        <p className="text-sm text-muted-foreground">Mã lỗi: {error.digest}</p>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <button
          className={publicButtonClass("primary")}
          onClick={() => retry()}
          type="button"
        >
          Thử lại
        </button>
        <Link className={publicButtonClass("secondary")} href="/portal">
          Về trang chủ
        </Link>
      </div>
    </div>
  );
}
