import { formatInteger } from "@/lib/format/formatNumber";
import type { RefundByPerson } from "../types/ownerFinance";
import { roleLabel, UNKNOWN_PERSON } from "../lib/ownerFinanceFormat";
import { OwnerPanel } from "./OwnerPanel";
import { RankedTable, type RankedColumn } from "./RankedTable";
import { RevenueShareCell } from "./RevenueShareCell";

/** Ai đã xác nhận hoàn tiền trong kỳ: số khoản và số tiền của từng người, kèm tỷ trọng. */
export function RefundByPersonPanel({ people }: { people: RefundByPerson[] }) {
  const total = people.reduce((sum, p) => sum + p.amount, 0);
  const columns: RankedColumn<RefundByPerson>[] = [
    {
      key: "name",
      label: "Người hoàn",
      sortValue: (p) => p.name ?? UNKNOWN_PERSON,
      cell: (p) => (
        <div className="whitespace-normal [overflow-wrap:anywhere]">
          <span className={p.name ? "font-medium" : "text-muted-foreground"}>
            {p.name ?? UNKNOWN_PERSON}
          </span>
          <p className="text-xs text-muted-foreground">
            {roleLabel(p.role)}
            {/* Màn hẹp: cột Số khoản ẩn, số khoản chuyển xuống dưới tên. */}
            <span className="sm:hidden">
              {p.role ? " · " : ""}
              {formatInteger(p.count)} khoản
            </span>
          </p>
        </div>
      ),
    },
    {
      key: "count",
      label: "Số khoản",
      align: "right",
      hideBelowSm: true,
      sortValue: (p) => p.count,
      cell: (p) => (
        <span className="tabular-nums">{formatInteger(p.count)}</span>
      ),
    },
    {
      key: "amount",
      label: "Đã hoàn",
      align: "right",
      sortValue: (p) => p.amount,
      cell: (p) => <RevenueShareCell revenue={p.amount} total={total} />,
    },
  ];

  return (
    <OwnerPanel
      title="Ai đã hoàn tiền"
      description="Người xác nhận các khoản hoàn trong kỳ"
    >
      {people.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">
          Không có khoản nào được hoàn trong kỳ này.
        </p>
      ) : (
        <RankedTable
          rows={people}
          columns={columns}
          rowKey={(p) => `${p.name ?? "unknown"}|${p.role ?? ""}`}
          defaultSortKey="amount"
          caption="Số khoản và số tiền đã hoàn theo người xác nhận"
        />
      )}
    </OwnerPanel>
  );
}
