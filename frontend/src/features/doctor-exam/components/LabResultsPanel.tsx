"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useLabResults } from "../hooks/useDoctorData";
import { BackendGapNotice } from "./BackendGapNotice";

// Kết quả xét nghiệm của lượt khám, chỉ xem: giá trị, đơn vị, khoảng tham chiếu và cờ bất thường
// ngay khi kỹ thuật viên duyệt (R3 màn 24, UC48). Bản live chưa có endpoint nên báo chờ BE.

export function LabResultsPanel({
  visitId,
  available,
}: {
  visitId: string;
  available: boolean;
}) {
  const { data, loading } = useLabResults(visitId);

  return (
    <section aria-labelledby="lab-title" className="space-y-3">
      <h2 id="lab-title" className="text-lg font-semibold">
        Kết quả xét nghiệm
      </h2>
      {!available ? (
        <BackendGapNotice title="Chưa có API xét nghiệm">
          Chỉ định và kết quả xét nghiệm sẽ hiện ở đây khi backend hỗ trợ.
        </BackendGapNotice>
      ) : loading ? (
        <Skeleton className="h-24 w-full" />
      ) : !data || data.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          Chưa có kết quả xét nghiệm nào được duyệt.
        </p>
      ) : (
        data.map((lab) => (
          <div
            key={lab.orderId}
            className="overflow-x-auto rounded-lg border bg-card"
          >
            <p className="border-b px-4 py-2.5 text-sm font-medium">
              {lab.serviceName}
            </p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Chỉ số</TableHead>
                  <TableHead>Giá trị</TableHead>
                  <TableHead>Đơn vị</TableHead>
                  <TableHead>Khoảng tham chiếu</TableHead>
                  <TableHead>Đánh giá</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lab.lines.map((line) => (
                  <TableRow key={line.indicator}>
                    <TableCell className="font-medium">
                      {line.indicator}
                    </TableCell>
                    <TableCell
                      className={
                        line.abnormal ? "font-semibold text-destructive" : ""
                      }
                    >
                      {line.value}
                    </TableCell>
                    <TableCell>{line.unit}</TableCell>
                    <TableCell>{line.reference}</TableCell>
                    <TableCell>
                      <StatusBadge tone={line.abnormal ? "danger" : "success"}>
                        {line.abnormal ? "Bất thường" : "Bình thường"}
                      </StatusBadge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ))
      )}
    </section>
  );
}
