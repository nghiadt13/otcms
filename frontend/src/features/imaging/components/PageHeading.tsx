"use client";

import type { ReactNode } from "react";
import { PageHeader } from "@/components/PageHeader";

export function PageHeading({
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="im-page-heading" title={description}>
      <PageHeader title={title} actions={children} />
    </div>
  );
}
