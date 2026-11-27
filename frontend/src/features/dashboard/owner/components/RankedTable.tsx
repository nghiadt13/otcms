"use client";

import { useMemo, useState, type ReactNode } from "react";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

export type RankedColumn<T> = {
  key: string;
  label: string;
  align?: "right";
  /** Giá trị dùng để sắp xếp cột này. */
  sortValue: (row: T) => number | string;
  cell: (row: T) => ReactNode;
  /** Ẩn cột ở màn hẹp để bảng không tràn ngang. */
  hideBelowSm?: boolean;
};

type Sort = { key: string; direction: "asc" | "desc" };

/**
 * Bảng xếp hạng sắp xếp được: nhấn tiêu đề cột để đổi chiều, `aria-sort` báo
 * trạng thái cho trình đọc màn hình. Dòng ghim (phần không xác định) luôn ở cuối.
 */
export function RankedTable<T>({
  rows,
  columns,
  rowKey,
  defaultSortKey,
  isPinnedLast,
  caption,
}: {
  rows: T[];
  columns: RankedColumn<T>[];
  rowKey: (row: T) => string;
  defaultSortKey: string;
  isPinnedLast?: (row: T) => boolean;
  caption: string;
}) {
  const [sort, setSort] = useState<Sort>({
    key: defaultSortKey,
    direction: "desc",
  });

  const sorted = useMemo(() => {
    const column = columns.find((c) => c.key === sort.key) ?? columns[0];
    const factor = sort.direction === "asc" ? 1 : -1;
    const ordered = [...rows].sort((a, b) => {
      const left = column.sortValue(a);
      const right = column.sortValue(b);
      if (typeof left === "string" || typeof right === "string")
        return factor * String(left).localeCompare(String(right), "vi");
      return factor * (left - right);
    });
    if (!isPinnedLast) return ordered;
    return [
      ...ordered.filter((r) => !isPinnedLast(r)),
      ...ordered.filter(isPinnedLast),
    ];
  }, [rows, columns, sort, isPinnedLast]);

  function toggle(key: string) {
    setSort((current) =>
      current.key === key
        ? { key, direction: current.direction === "desc" ? "asc" : "desc" }
        : { key, direction: "desc" },
    );
  }

  return (
    <Table>
      <caption className="sr-only">{caption}</caption>
      <TableHeader>
        <TableRow>
          {columns.map((column) => {
            const active = sort.key === column.key;
            const Icon = !active
              ? ChevronsUpDown
              : sort.direction === "desc"
                ? ArrowDown
                : ArrowUp;
            return (
              <TableHead
                key={column.key}
                aria-sort={
                  active
                    ? sort.direction === "desc"
                      ? "descending"
                      : "ascending"
                    : "none"
                }
                className={cn(
                  column.align === "right" && "text-right",
                  column.hideBelowSm && "hidden sm:table-cell",
                )}
              >
                <button
                  type="button"
                  onClick={() => toggle(column.key)}
                  className={cn(
                    "inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-sm font-medium outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring",
                    active ? "text-foreground" : "text-muted-foreground",
                    column.align === "right" && "flex-row-reverse",
                  )}
                >
                  {column.label}
                  <Icon className="size-3.5" aria-hidden />
                </button>
              </TableHead>
            );
          })}
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((row) => (
          <TableRow key={rowKey(row)}>
            {columns.map((column) => (
              <TableCell
                key={column.key}
                className={cn(
                  column.align === "right" && "text-right",
                  column.hideBelowSm && "hidden sm:table-cell",
                )}
              >
                {column.cell(row)}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
