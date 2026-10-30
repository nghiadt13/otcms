"use client";

import { Badge } from "./Badge";

export function OrderBadge({ status }: { status: string }) {
  const labels: Record<string, string> = {
    "Pending Payment": "Chờ thanh toán",
    Paid: "Đã thanh toán",
    "Results Available": "Đã có kết quả",
    Cancelled: "Đã hủy",
  };
  return (
    <Badge
      tone={
        status === "Paid"
          ? "blue"
          : status === "Results Available"
            ? "green"
            : status === "Cancelled"
              ? "red"
              : "amber"
      }
    >
      {labels[status] || status}
    </Badge>
  );
}
