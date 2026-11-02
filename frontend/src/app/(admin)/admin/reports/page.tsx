import type { Metadata } from "next";
import { ReportsManagement } from "@/features/reports/components/ReportsManagement";

export const metadata: Metadata = { title: "Báo cáo" };

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/** Chỉ nhận ngày đúng dạng yyyy-MM-dd; giá trị khác bị bỏ. */
function isoParam(value: string | string[] | undefined): string | undefined {
  const text = Array.isArray(value) ? value[0] : value;
  return text && ISO_DATE.test(text) ? text : undefined;
}

export default async function AdminReportsPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  return (
    <ReportsManagement
      initialFrom={isoParam(params.from)}
      initialTo={isoParam(params.to)}
    />
  );
}
