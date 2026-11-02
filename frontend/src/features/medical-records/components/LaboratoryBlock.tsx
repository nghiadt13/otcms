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
  return (
    <li className="flex flex-wrap items-baseline gap-x-4 gap-y-1 px-4 py-3 md:grid md:grid-cols-[minmax(0,1.6fr)_minmax(0,1.1fr)_minmax(0,0.9fr)_minmax(0,1.4fr)]">
      <div className="flex w-full items-baseline justify-between gap-3 md:contents">
        <p className="text-base font-medium md:order-1">{line.indicator}</p>
        <span className="md:order-3">
          <FlagMark flag={line.flag} />
        </span>
      </div>
      <p className="text-base tabular-nums md:order-2">
        <span className="font-semibold">{line.value}</span>{" "}
        <span className="text-muted-foreground">{line.unit}</span>
      </p>
      <p className="text-base text-muted-foreground tabular-nums md:order-4">
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
        <div key={group.serviceName} className="space-y-2">
          <h3 className="text-base font-semibold">{group.serviceName}</h3>
          <div className="rounded-lg border">
            <div
              aria-hidden
              className="hidden border-b bg-muted/50 px-4 py-2 text-sm text-muted-foreground md:grid md:grid-cols-[minmax(0,1.6fr)_minmax(0,1.1fr)_minmax(0,0.9fr)_minmax(0,1.4fr)] md:gap-x-4"
            >
              <span className="md:order-1">Chỉ số</span>
              <span className="md:order-2">Kết quả</span>
              <span className="md:order-3">Cờ</span>
              <span className="md:order-4">Khoảng tham chiếu</span>
            </div>
            <ul className="divide-y">
              {group.lines.map((line) => (
                <LabRow key={line.indicator} line={line} />
              ))}
            </ul>
          </div>
        </div>
      ))}
    </div>
  );
}
