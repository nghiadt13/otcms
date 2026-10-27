import { Fragment, useId } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import { buttonVariants } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { toQueryString } from "@/lib/api/pagination";
import {
  NO_SOURCE_TEXT,
  type DashboardBlockView,
} from "../lib/dashboardBlockConfig";
import {
  describeComparison,
  formatMetricValue,
} from "../lib/describeComparison";
import type { DashboardPeriod } from "../types/dashboardOverview";

type DashboardBlockSectionProps = {
  block: DashboardBlockView;
  /** Kỳ của số liệu đang hiện, gắn vào link sang module đích. */
  period: DashboardPeriod;
};

/** Một khối: tiêu đề, mô tả, link bên trái; bảng ngắn bên phải (xếp dọc ở màn hẹp). */
export function DashboardBlockSection({
  block,
  period,
}: DashboardBlockSectionProps) {
  const headingId = useId();
  const href = `${block.href}${toQueryString(period)}`;
  const emptyMessage = block.noSource ? NO_SOURCE_TEXT : block.emptyNotice;

  return (
    <section
      aria-labelledby={headingId}
      className="grid gap-4 py-6 first:pt-0 last:pb-0 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-8"
    >
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h2 id={headingId} className="text-lg font-semibold">
            {block.title}
          </h2>
          {block.noSource ? (
            <StatusBadge tone="neutral">Chưa có dữ liệu</StatusBadge>
          ) : null}
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          {block.description}
        </p>
        <Link
          href={href}
          className={buttonVariants({
            variant: "link",
            className: "mt-2 h-auto px-0",
          })}
        >
          Xem chi tiết
          <span className="sr-only">{block.title}</span>
          <ArrowRight aria-hidden />
        </Link>
      </div>
      <div className="min-w-0">
        {block.rows.length > 0 ? <BlockTable block={block} /> : null}
        {emptyMessage ? (
          <p
            className={
              block.rows.length === 0
                ? "rounded-lg border bg-card px-4 py-6 text-sm text-muted-foreground"
                : "mt-2 text-sm text-muted-foreground"
            }
          >
            {emptyMessage}
          </p>
        ) : null}
      </div>
    </section>
  );
}

function BlockTable({ block }: { block: DashboardBlockView }) {
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Chỉ số</TableHead>
            <TableHead className="text-right">Giá trị</TableHead>
            <TableHead>So với kỳ trước</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {block.rows.map((row, index) => {
            const showGroup =
              row.group !== undefined &&
              row.group !== block.rows[index - 1]?.group;
            return (
              <Fragment key={`${row.group ?? ""}-${row.label}-${index}`}>
                {showGroup ? (
                  <TableRow className="bg-muted/50 hover:bg-muted/50">
                    <TableCell
                      colSpan={3}
                      className="py-1.5 text-xs font-medium text-muted-foreground"
                    >
                      {row.group}
                    </TableCell>
                  </TableRow>
                ) : null}
                <TableRow>
                  <TableCell>
                    {row.label}
                    {row.hint ? (
                      <span className="block text-xs text-muted-foreground">
                        {row.hint}
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell className="text-right font-medium tabular-nums">
                    {formatMetricValue(row.value, row.format)}
                    {block.noSource ? (
                      <span className="sr-only">Chưa có dữ liệu</span>
                    ) : null}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {block.noSource
                      ? "—"
                      : (row.note ?? describeComparison(row))}
                  </TableCell>
                </TableRow>
              </Fragment>
            );
          })}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
