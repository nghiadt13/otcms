import { addDays } from "@/lib/format/formatDate";
import type {
  ReportColumn,
  ReportQuery,
  ReportRow,
  ReportSection,
} from "../types/report";

const RECEPTIONISTS = ["Nguyễn Thu Hà", "Trần Minh Châu", "Lê Bảo Ngọc"];

/** Số giả ổn định theo khóa: cùng kỳ luôn ra cùng số liệu. */
export function pick(key: string, min: number, max: number): number {
  let h = 2166136261;
  for (let i = 0; i < key.length; i++) h = Math.imul(h ^ key.charCodeAt(i), 16777619);
  return min + (Math.abs(h) % (max - min + 1));
}

export function listDays(from: string, to: string): string[] {
  const days: string[] = [];
  for (let d = from; d <= to && days.length < 366; d = addDays(d, 1)) days.push(d);
  return days;
}

export const col = (
  key: string,
  label: string,
  kind: ReportColumn["kind"],
): ReportColumn => ({ key, label, kind });

/** Giả lập BE: dòng tổng cộng các cột số, nhãn cột đầu "Tổng cộng". */
export function makeSection(
  title: string,
  columns: ReportColumn[],
  rows: ReportRow[],
  withTotal = true,
): ReportSection {
  let totalRow: ReportRow | null = null;
  if (withTotal && rows.length > 0) {
    totalRow = { [columns[0].key]: "Tổng cộng" };
    for (const c of columns.slice(1))
      totalRow[c.key] = rows.reduce((sum, r) => sum + Number(r[c.key] ?? 0), 0);
  }
  return { title, columns, rows, totalRow };
}

type Cell = {
  day: string;
  name: string;
  cashN: number;
  vnN: number;
  cash: number;
  vnpay: number;
};

/** Thanh toán: mọi bảng cộng từ cùng một tập (ngày, lễ tân) nên Tiền mặt + VNPay = Tổng thu. */
export function buildPaymentSections(q: ReportQuery): ReportSection[] {
  const days = listDays(q.from, q.to);
  const cells: Cell[] = days.flatMap((day) =>
    RECEPTIONISTS.map((name) => {
      const cashN = pick(`c${day}${name}`, 0, 6);
      const vnN = pick(`v${day}${name}`, 0, 5);
      return {
        day,
        name,
        cashN,
        vnN,
        cash: cashN * pick(`cp${day}${name}`, 15, 40) * 10000,
        vnpay: vnN * pick(`vp${day}${name}`, 20, 60) * 10000,
      };
    }),
  );
  const columns = (head: string, key: string, kind: ReportColumn["kind"]) => [
    col(key, head, kind),
    col("count", "Số giao dịch", "count"),
    col("cash", "Tiền mặt", "money"),
    col("vnpay", "VNPay", "money"),
    col("total", "Tổng thu", "money"),
  ];
  const sumRow = (first: ReportRow, items: Cell[]): ReportRow => {
    const cash = items.reduce((s, c) => s + c.cash, 0);
    const vnpay = items.reduce((s, c) => s + c.vnpay, 0);
    return {
      ...first,
      count: items.reduce((s, c) => s + c.cashN + c.vnN, 0),
      cash,
      vnpay,
      total: cash + vnpay,
    };
  };

  if (q.breakdown === "DATE")
    return [
      makeSection("Theo ngày", columns("Ngày", "day", "date"),
        days.map((d) => sumRow({ day: d }, cells.filter((c) => c.day === d)))),
    ];
  if (q.breakdown === "RECEPTIONIST")
    return [
      makeSection("Theo lễ tân", columns("Lễ tân", "name", "text"),
        RECEPTIONISTS.map((n) => sumRow({ name: n }, cells.filter((c) => c.name === n)))),
    ];
  if (q.breakdown === "PAYMENT_METHOD") {
    const all = sumRow({}, cells);
    return [
      makeSection(
        "Theo phương thức",
        [col("method", "Phương thức", "text"), col("count", "Số giao dịch", "count"), col("total", "Tổng thu", "money")],
        [
          { method: "Tiền mặt", count: cells.reduce((s, c) => s + c.cashN, 0), total: all.cash },
          { method: "VNPay", count: cells.reduce((s, c) => s + c.vnN, 0), total: all.vnpay },
        ],
      ),
    ];
  }
  return [
    makeSection("Toàn kỳ", columns("Phạm vi", "scope", "text"),
      [sumRow({ scope: "Toàn kỳ" }, cells)], false),
  ];
}
