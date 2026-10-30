"use client";

import { Button } from "@/components/ui/button";

export function Pagination({
  page,
  total,
  size = 5,
  onChange,
}: {
  page: number;
  total: number;
  size?: number;
  onChange: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const current = Math.min(page, pages);
  return (
    <footer className="im-pagination">
      <span>
        {total
          ? `${(current - 1) * size + 1}–${Math.min(current * size, total)} trong ${total} mục`
          : "0 mục"}
      </span>
      <div>
        <Button
          variant="outline"
          size="sm"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
        >
          Trước
        </Button>
        <span>
          {current} / {pages}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={current >= pages}
          onClick={() => onChange(current + 1)}
        >
          Sau
        </Button>
      </div>
    </footer>
  );
}
