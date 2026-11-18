import { ArrowDown, ArrowUp } from "lucide-react";
import type { LabFlag, LabGroup, LabLine } from "../types/examinationResult";

// Cờ bất thường luôn có chữ (không chỉ màu): "Cao" / "Thấp" đậm kèm mũi tên.
const FLAG_TEXT: Record<LabFlag, string> = { HIGH: "Cao", LOW: "Thấp" };

function FlagMark({ flag }: { flag: LabFlag | null }) {
  if (!flag) return null;
  const Icon = flag === "HIGH" ? ArrowUp : ArrowDown;
  return (
    <span className="inline-flex items-center gap-1 font-semibold text-warning">
      <Icon aria-hidden className="size-4" />
      {FLAG_TEXT[flag]}
    </span>
  );
}

/**
 * Một chỉ số. Ở 375px là khối hai dòng: tên + cờ, rồi giá trị + đơn vị + khoảng.
 * Từ md trở lên thành một hàng bốn cột (chỉ số · giá trị · cờ · khoảng tham chiếu).
 * Cùng một DOM, chỉ đổi bố cục bằng class, nên không cuộn ngang và không lặp chữ.
 */
function LabRow({ line }: { line: LabLine }) {
  const classification = line.abnormal === undefined
    ? null
    : line.abnormal === null
      ? "Chưa xác định"
      : line.abnormal
        ? "Bất thường"
        : "Bình thường";
  return (
    <li className="grid min-w-0 grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 px-3 py-3 sm:px-4 md:grid-cols-[minmax(0,1.6fr)_minmax(0,1.1fr)_minmax(0,0.9fr)_minmax(0,1.4fr)] md:gap-x-4">
      <p className="min-w-0 break-words text-base font-medium md:order-1">{line.indicator}</p>
      <span className="justify-self-end md:order-3 md:justify-self-start">
        {classification ? (
          <span
            className={
              line.abnormal === true
                ? "inline-flex items-center rounded-full bg-destructive/10 px-2 py-0.5 text-xs font-semibold text-destructive"
                : line.abnormal === false
                  ? "inline-flex items-center rounded-full bg-success-soft px-2 py-0.5 text-xs font-semibold text-success"
                  : "inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground"
            }
          >
            {classification}
          </span>
        ) : <FlagMark flag={line.flag ?? null} />}
      </span>
      <p className="min-w-0 text-base tabular-nums md:order-2">
        <span className="font-semibold">{line.value}</span>{" "}
        <span className="text-muted-foreground">{line.unit}</span>
      </p>
      <p className="min-w-0 break-words text-right text-base text-muted-foreground tabular-nums md:order-4 md:text-left">
        {line.referenceRange}
      </p>
    </li>
  );
}

/** Khối "Xét nghiệm": mỗi nhóm dịch vụ một danh sách chỉ số trong một khung có vạch chia. */
export function LaboratoryBlock({ groups }: { groups: LabGroup[] }) {
  if (groups.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Lượt khám này chưa có kết quả xét nghiệm.
      </p>
    );
  return (
    <div className="space-y-6">
      {groups.map((group) => (
        <div key={group.orderId ?? group.serviceName} className="space-y-2">
          <h3 className="text-base font-semibold">{group.serviceName}</h3>
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
            <div className="min-w-0 flex-1 rounded-lg border">
              <div
                aria-hidden
                className="hidden border-b bg-muted/50 px-4 py-2 text-sm text-muted-foreground md:grid md:grid-cols-[minmax(0,1.6fr)_minmax(0,1.1fr)_minmax(0,0.9fr)_minmax(0,1.4fr)] md:gap-x-4"
              >
                <span className="md:order-1">Chỉ số</span>
                <span className="md:order-2">Kết quả</span>
                <span className="md:order-3">Trạng thái</span>
                <span className="md:order-4">Khoảng tham chiếu</span>
              </div>
              <ul className="divide-y">
                {group.lines.map((line) => (
                  <LabRow key={line.indicator} line={line} />
                ))}
              </ul>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
