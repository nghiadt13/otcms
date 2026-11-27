import { formatDecimal1, formatInteger } from "@/lib/format/formatNumber";
import type { DoctorPerformanceRow } from "../types/ownerAnalytics";
import { averagePerDay } from "../lib/ownerAnalyticsFormat";
import { DeltaChip } from "./DeltaChip";
import { OwnerPanel } from "./OwnerPanel";
import { RankedTable, type RankedColumn } from "./RankedTable";
import { RevenueShareCell } from "./RevenueShareCell";

/** Bác sĩ: số ca khám, ca trung bình mỗi ngày có khám, và doanh thu kèm tỷ trọng. */
export function DoctorRevenueTable({
  doctors,
}: {
  doctors: DoctorPerformanceRow[];
}) {
  const total = doctors.reduce((sum, d) => sum + d.revenue, 0);
  const columns: RankedColumn<DoctorPerformanceRow>[] = [
    {
      key: "name",
      label: "Bác sĩ",
      sortValue: (d) => d.name,
      cell: (d) =>
        d.doctorId === null ? (
          <div className="whitespace-normal">
            <span className="text-muted-foreground">{d.name}</span>
            <p className="text-xs text-muted-foreground">
              Thanh toán không truy được bác sĩ
            </p>
          </div>
        ) : (
          <div className="whitespace-normal">
            <span className="font-medium">{d.name}</span>
            {/* Màn hẹp: cột Ca khám ẩn, số ca chuyển xuống dưới tên. */}
            <p className="text-xs text-muted-foreground tabular-nums sm:hidden">
              {formatInteger(d.visits)} ca khám
            </p>
          </div>
        ),
    },
    {
      key: "visits",
      label: "Ca khám",
      align: "right",
      hideBelowSm: true,
      sortValue: (d) => d.visits,
      cell: (d) =>
        d.doctorId === null ? (
          "—"
        ) : (
          <span className="tabular-nums">{formatInteger(d.visits)}</span>
        ),
    },
    {
      key: "average",
      label: "TB ca/ngày",
      align: "right",
      hideBelowSm: true,
      sortValue: (d) => averagePerDay(d.visits, d.activeDays) ?? 0,
      cell: (d) => {
        const average = averagePerDay(d.visits, d.activeDays);
        return average === null ? (
          "—"
        ) : (
          <span className="tabular-nums">{formatDecimal1(average)}</span>
        );
      },
    },
    {
      key: "revenue",
      label: "Doanh thu",
      align: "right",
      sortValue: (d) => d.revenue,
      cell: (d) => <RevenueShareCell revenue={d.revenue} total={total} />,
    },
    {
      key: "delta",
      label: "So kỳ trước",
      align: "right",
      hideBelowSm: true,
      sortValue: (d) =>
        d.previousRevenue
          ? (d.revenue - d.previousRevenue) / d.previousRevenue
          : 0,
      cell: (d) => <DeltaChip value={d.revenue} previous={d.previousRevenue} />,
    },
  ];

  return (
    <OwnerPanel
      title="Bác sĩ"
      description="Doanh thu và số ca khám theo bác sĩ. Phí khám tính cho bác sĩ của lịch hẹn, phí chụp và xét nghiệm tính cho bác sĩ chỉ định"
    >
      <RankedTable
        rows={doctors}
        columns={columns}
        rowKey={(d) => d.doctorId ?? "unattributed"}
        defaultSortKey="revenue"
        isPinnedLast={(d) => d.doctorId === null}
        caption="Doanh thu và số ca khám theo bác sĩ"
      />
    </OwnerPanel>
  );
}
