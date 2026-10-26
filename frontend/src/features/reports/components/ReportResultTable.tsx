import { TableFrame } from "@/components/admin/TableFrame";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate } from "@/lib/format/formatDate";
import { formatDecimal1, formatInteger } from "@/lib/format/formatNumber";
import type { ReportCell, ReportColumn, ReportSection } from "../types/report";

const NUMERIC_KINDS = new Set<ReportColumn["kind"]>([
  "count",
  "money",
  "score",
]);

/** Định dạng ô theo `kind`; chuỗi không phải ngày (ví dụ nhãn "Tổng cộng") giữ nguyên. */
function formatCell(
  value: ReportCell | undefined,
  kind: ReportColumn["kind"],
): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") {
    return kind === "date" && /^\d{4}-\d{2}-\d{2}$/.test(value)
      ? formatDate(value)
      : value;
  }
  if (kind === "money") return formatCurrencyVnd(value);
  if (kind === "score") return formatDecimal1(value);
  return formatInteger(value);
}

/** Một bảng nhỏ của báo cáo. Dòng tổng do BE trả, FE không cộng lại. */
export function ReportResultTable({ section }: { section: ReportSection }) {
  return (
    <div className="grid gap-2">
      <h3 className="text-sm font-medium">{section.title}</h3>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              {section.columns.map((c, i) => (
                <TableHead
                  key={c.key}
                  className={cn(
                    NUMERIC_KINDS.has(c.kind) && "text-right",
                    i === 0 && "sticky left-0 bg-card",
                  )}
                >
                  {c.label}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {section.rows.map((row, r) => (
              <ReportRowCells key={r} section={section} row={row} />
            ))}
            {section.totalRow ? (
              <ReportRowCells section={section} row={section.totalRow} total />
            ) : null}
          </TableBody>
        </Table>
      </TableFrame>
    </div>
  );
}

function ReportRowCells({
  section,
  row,
  total = false,
}: {
  section: ReportSection;
  row: Record<string, ReportCell>;
  total?: boolean;
}) {
  return (
    <TableRow
      className={cn(total && "border-t-2 font-semibold hover:bg-transparent")}
    >
      {section.columns.map((c, i) => (
        <TableCell
          key={c.key}
          className={cn(
            "tabular-nums",
            NUMERIC_KINDS.has(c.kind) && "text-right",
            i === 0 && "sticky left-0 bg-card",
          )}
        >
          {formatCell(row[c.key], c.kind)}
        </TableCell>
      ))}
    </TableRow>
  );
}
