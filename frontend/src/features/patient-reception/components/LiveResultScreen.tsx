"use client";

import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { apiRequest } from "@/lib/api/client";
import { formatVisitDisplayId } from "@/lib/format/visitDisplayId";
import { messageFromError } from "@/lib/messages/appMessages";
import { normalizeText } from "../schema/receptionRules";
import { DataTable, PatientName, TextField, timeText } from "./ReceptionUi";
import { VisitResultSheets } from "./VisitResultSheets";

type ReceptionResultVisit = {
  visitId: string;
  patientId: string;
  patientCode?: string | null;
  patientName: string | null;
  status: string;
  arrivedAt: string;
};
type ReceptionResultVisitPage = {
  page: number;
  hasMore: boolean;
  items: ReceptionResultVisit[];
};

export function LiveResultScreen() {
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const result = useAdminRemoteData(
    useCallback(
      (signal: AbortSignal) =>
        apiRequest<ReceptionResultVisitPage>(
          `/api/v1/visits?status=CLOSED&page=${page}&size=20`,
          { signal },
        ),
      [page],
    ),
  );
  const visits = (result.data?.items ?? []).filter((visit) =>
    normalizeText(
      `${visit.patientName ?? ""} ${visit.patientId} ${visit.visitId} ${formatVisitDisplayId(visit.visitId)}`,
    ).includes(normalizeText(search)),
  );

  return (
    <>
      <PageHeader
        title="Phiếu kết quả khám"
        description="Mở phiếu đã phát hành từ dữ liệu lượt khám và hồ sơ thật. Lễ tân chỉ xem hoặc in."
      />
      <div className="mb-4 max-w-lg">
        <TextField
          label="Tên bệnh nhân / mã hồ sơ / lượt khám"
          value={search}
          onChange={setSearch}
        />
      </div>
      {result.loading && <p role="status">Đang tải lượt khám đã kết thúc…</p>}
      {result.error != null && (
        <div role="alert" className="mb-4 space-y-2">
          <p>{messageFromError(result.error)}</p>
          <Button variant="outline" onClick={result.refresh}>
            Thử lại
          </Button>
        </div>
      )}
      <DataTable
        key={`${search}-${page}`}
        label="Phiếu kết quả khám"
        headers={["Lượt khám", "Bệnh nhân", "Tiếp nhận", "Phiếu đã phát hành"]}
        rows={visits.map((visit) => ({
          id: visit.visitId,
          cells: [
            formatVisitDisplayId(visit.visitId),
            <PatientName
              key="patient"
              name={visit.patientName ?? "—"}
              code={visit.patientCode ?? visit.patientId}
            />,
            timeText(visit.arrivedAt),
            <div key="sheets" className="min-w-52">
              <VisitResultSheets visitId={visit.visitId} />
            </div>,
          ],
        }))}
        empty="Không có lượt khám đã kết thúc phù hợp."
      />
      {result.data && (
        <div className="mt-4 flex gap-2">
          <Button
            variant="outline"
            disabled={page === 0 || result.loading}
            onClick={() => setPage((value) => value - 1)}
          >
            Trang trước
          </Button>
          <Button
            variant="outline"
            disabled={!result.data.hasMore || result.loading}
            onClick={() => setPage((value) => value + 1)}
          >
            Trang sau
          </Button>
        </div>
      )}
    </>
  );
}
