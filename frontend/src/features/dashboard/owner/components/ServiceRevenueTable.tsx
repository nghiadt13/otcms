import { CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { formatInteger } from "@/lib/format/formatNumber";
import type { ServiceRevenueRow } from "../types/ownerAnalytics";
import { DeltaChip } from "./DeltaChip";
import { OwnerPanel } from "./OwnerPanel";
import { RankedTable, type RankedColumn } from "./RankedTable";
import { RevenueShareCell } from "./RevenueShareCell";

/** Dịch vụ: số lượt dùng và doanh thu kèm tỷ trọng, xếp theo doanh thu. */
export function ServiceRevenueTable({
  services,
}: {
  services: ServiceRevenueRow[];
}) {
  const total = services.reduce((sum, s) => sum + s.revenue, 0);
  const columns: RankedColumn<ServiceRevenueRow>[] = [
    {
      key: "name",
      label: "Dịch vụ",
      sortValue: (s) => s.name,
      cell: (s) => (
        <div className="whitespace-normal">
          <span className="font-medium">{s.name}</span>
          <p className="text-xs text-muted-foreground">
            {CATEGORY_LABELS[s.category]}
            {/* Màn hẹp: cột Lượt ẩn, số lượt chuyển xuống dưới tên. */}
            <span className="tabular-nums sm:hidden">
              {" · "}
              {formatInteger(s.count)} lượt
            </span>
          </p>
        </div>
      ),
    },
    {
      key: "count",
      label: "Lượt",
      align: "right",
      hideBelowSm: true,
      sortValue: (s) => s.count,
      cell: (s) => (
        <span className="tabular-nums">{formatInteger(s.count)}</span>
      ),
    },
    {
      key: "revenue",
      label: "Doanh thu",
      align: "right",
      sortValue: (s) => s.revenue,
      cell: (s) => <RevenueShareCell revenue={s.revenue} total={total} />,
    },
    {
      key: "delta",
      label: "So kỳ trước",
      align: "right",
      hideBelowSm: true,
      sortValue: (s) =>
        s.previousRevenue
          ? (s.revenue - s.previousRevenue) / s.previousRevenue
          : 0,
      cell: (s) => <DeltaChip value={s.revenue} previous={s.previousRevenue} />,
    },
  ];

  return (
    <OwnerPanel
      title="Dịch vụ"
      description="Khoản nào trong bảng giá đem lại nhiều doanh thu nhất"
    >
      <RankedTable
        rows={services}
        columns={columns}
        rowKey={(s) => s.serviceId}
        defaultSortKey="revenue"
        caption="Doanh thu và số lượt theo dịch vụ"
      />
    </OwnerPanel>
  );
}
