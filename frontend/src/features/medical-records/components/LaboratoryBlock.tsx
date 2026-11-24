import { ArrowDown, ArrowUp } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import type { LabFlag, LabGroup, LabLine } from "../types/examinationResult";

// Cờ bất thường luôn có chữ (không chỉ màu): "Cao" / "Thấp" đậm kèm mũi tên.
const FLAG_TEXT: Record<LabFlag, string> = { HIGH: "Cao", LOW: "Thấp" };

function FlagMark({ flag }: { flag: LabFlag | null }) {
  if (!flag) return null;
  const Icon = flag === "HIGH" ? ArrowUp : ArrowDown;
  return (
    <StatusBadge tone="warning" className="h-auto gap-1 py-0.5 text-sm">
      <Icon aria-hidden className="size-4 shrink-0" />
      {FLAG_TEXT[flag]}
    </StatusBadge>
  );
}

/**
 * Một hàng bảng trên màn rộng, xếp kết quả và khoảng tham chiếu có nhãn trên màn hẹp.
 */
function LabRow({ line }: { line: LabLine }) {
  const classification =
    line.abnormal === undefined
      ? null
      : line.abnormal === null
        ? "Chưa xác định"
        : line.abnormal
          ? "Bất thường"
          : "Bình thường";
  return (
    <tr
      role="row"
      className="grid grid-cols-2 gap-x-3 gap-y-2 px-3 py-2.5 sm:table-row"
    >
      <th
        role="rowheader"
        scope="row"
        className="col-span-2 wrap-break-word text-left text-base font-medium sm:px-3 sm:py-2.5"
      >
        {line.indicator}
      </th>
      <td
        role="cell"
        className="wrap-break-word text-base tabular-nums sm:px-3 sm:py-2.5"
      >
        <span
          aria-hidden
          className="block text-sm text-muted-foreground sm:hidden"
        >
          Kết quả
        </span>
        <span className="font-semibold">{line.value}</span>{" "}
        <span className="text-muted-foreground">{line.unit}</span>
      </td>
      <td
        role="cell"
        className="wrap-break-word text-base text-muted-foreground tabular-nums sm:px-3 sm:py-2.5"
      >
        <span aria-hidden className="block text-sm sm:hidden">
          Khoảng tham chiếu
        </span>
        {line.referenceRange}
      </td>
      <td
        role="cell"
        className="col-span-2 max-sm:empty:hidden sm:px-3 sm:py-2.5"
      >
        {classification ? (
          <StatusBadge
            tone={
              line.abnormal === null
                ? "neutral"
                : line.abnormal
                  ? "danger"
                  : "success"
            }
            className="h-auto py-0.5 text-sm whitespace-normal"
          >
            {classification}
          </StatusBadge>
        ) : (
          <FlagMark flag={line.flag ?? null} />
        )}
      </td>
    </tr>
  );
}

/** Khối "Xét nghiệm": mỗi nhóm dịch vụ có một bảng chỉ số. */
export function LaboratoryBlock({ groups }: { groups: LabGroup[] }) {
  if (groups.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Lượt khám này chưa có kết quả xét nghiệm.
      </p>
    );
  return (
    <div className="space-y-4">
      {groups.map((group) => (
        <div
          key={group.orderId ?? group.serviceName}
          className="overflow-hidden rounded-lg border"
        >
          <h3 className="border-b bg-muted/50 px-3 py-2 text-base font-semibold">
            {group.serviceName}
          </h3>
          <table
            aria-label={group.serviceName}
            role="table"
            className="block w-full table-fixed text-left sm:table"
          >
            <thead
              role="rowgroup"
              className="sr-only border-b bg-muted/50 text-sm text-muted-foreground sm:not-sr-only sm:table-header-group"
            >
              <tr
                role="row"
                className="[&>th]:px-3 [&>th]:py-2 [&>th]:font-medium"
              >
                <th role="columnheader" className="w-[27%]" scope="col">
                  Chỉ số
                </th>
                <th role="columnheader" className="w-[22%]" scope="col">
                  Kết quả
                </th>
                <th role="columnheader" className="w-[29%]" scope="col">
                  Khoảng tham chiếu
                </th>
                <th role="columnheader" className="w-[22%]" scope="col">
                  Trạng thái
                </th>
              </tr>
            </thead>
            <tbody
              role="rowgroup"
              className="block divide-y sm:table-row-group"
            >
              {group.lines.map((line) => (
                <LabRow key={line.indicator} line={line} />
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  );
}
