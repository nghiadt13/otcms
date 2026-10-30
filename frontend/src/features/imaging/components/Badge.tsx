"use client";

import type { ReactNode } from "react";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";

const tones: Record<string, StatusTone> = {
  blue: "info",
  purple: "info",
  green: "success",
  amber: "warning",
  red: "danger",
  hero: "info",
};

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return (
    <StatusBadge className="im-badge" tone={tones[tone] ?? "neutral"}>
      {children}
    </StatusBadge>
  );
}
