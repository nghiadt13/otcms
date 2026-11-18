"use client";
import { useCallback, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { apiRequest } from "@/lib/api/client";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  DataTable,
  FormModal,
  InfoRow,
  PatientName,
  SelectField,
  TextField,
  timeText,
} from "./ReceptionUi";
import {
  searchReceptionPatients,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import { VisitResultSheets } from "./VisitResultSheets";

type Visit = {
  visitId: string;
  patientId: string;
  patientName: string | null;
  status: string;
  arrivedAt: string;
};
type Page = { items: Visit[]; hasMore: boolean };
type Progress = {
  visitId: string;
  status: string;
  currentStage: string;
  arrivedAt: string;
  currentRoom: string | null;
  queueNumber: number | null;
  peopleAhead: number | null;
  beingCalled: boolean;
  serviceOrders: {
    orderId: string;
    orderCode: string;
    serviceName: string;
    status: string;
    paymentStatus: string;
    roomName: string | null;
  }[];
};
const labels: Record<string, string> = {
  OPEN: "Đang tiếp nhận hoặc khám",
  CLOSED: "Đã kết thúc",
  INCOMPLETE: "Chưa kết luận",
  WAITING: "Chờ khám",
  IN_EXAMINATION: "Đang khám",
  AWAITING_CONCLUSION: "Chờ kết luận",
  ORDERED: "Đã chỉ định",
  RESULTS_AVAILABLE: "Đã có kết quả",
  CANCELLED: "Đã hủy",
  PAID: "Đã thanh toán",
  PENDING_PAYMENT: "Chờ thanh toán",
};

export function VisitDetail({
  visitId,
  onClose,
}: {
  visitId: string;
  onClose: () => void;
}) {
  const result = useAdminRemoteData(
    useCallback(
      (signal: AbortSignal) =>
        apiRequest<Progress>(
          `/api/v1/visits/${encodeURIComponent(visitId)}/progress`,
          { signal },
        ),
      [visitId],
    ),
  );
  const v = result.data;
  return (
    <FormModal
      title={`Tiến trình ${visitId}`}
      description="Theo dõi tiến trình lượt khám."
      onClose={onClose}
      wide
    >
      {result.loading && <p>Đang tải tiến trình…</p>}
      {result.error != null && (
        <div role="alert">
          {messageFromError(result.error)}{" "}
          <Button variant="outline" onClick={result.refresh}>
            Thử lại
          </Button>
        </div>
      )}
      {v && (
        <>
          <dl>
            <InfoRow label="Trạng thái">
              {labels[v.status] ?? v.status} ·{" "}
              {labels[v.currentStage] ?? v.currentStage}
            </InfoRow>
            <InfoRow label="Tiếp nhận">{timeText(v.arrivedAt)}</InfoRow>
            <InfoRow label="Phòng hiện tại">{v.currentRoom ?? "—"}</InfoRow>
            <InfoRow label="Số thứ tự">
              {v.queueNumber ?? "—"}
              {v.peopleAhead != null
                ? ` · ${v.peopleAhead} người phía trước`
                : ""}
              {v.beingCalled ? " · Đang gọi" : ""}
            </InfoRow>
          </dl>
          <section>
            <h2 className="text-lg font-semibold">Chỉ định cận lâm sàng</h2>
            {v.serviceOrders.length ? (
              <ul className="divide-y rounded-lg border">
                {v.serviceOrders.map((o) => (
                  <li className="p-3" key={o.orderId}>
                    {o.serviceName} · {o.roomName ?? "—"} ·{" "}
                    {labels[o.status] ?? o.status} ·{" "}
                    {labels[o.paymentStatus] ?? o.paymentStatus}
                  </li>
                ))}
              </ul>
            ) : (
              <p>Chưa có chỉ định.</p>
            )}
          </section>
          {v.status === "CLOSED" && <VisitResultSheets visitId={v.visitId} />}
        </>
      )}
    </FormModal>
  );
}
export function VisitScreen() {
  const params = useSearchParams();
  const [selected, setSelected] = useState<string | null>(
    params.get("visitId"),
  );
  const [keyword, setKeyword] = useState("");
  const [matches, setMatches] = useState<ReceptionPatient[]>([]);
  const [searchError, setSearchError] = useState("");
  const [searching, setSearching] = useState(false);
  const [filter, setFilter] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const result = useAdminRemoteData(
    useCallback(
      (signal: AbortSignal) => {
        const query = new URLSearchParams({ page: String(page), size: "50" });
        if (filter) query.set("patientId", filter);
        if (status) query.set("status", status);
        return apiRequest<Page>(`/api/v1/visits?${query}`, { signal });
      },
      [page, filter, status],
    ),
  );
  return (
    <>
      <PageHeader
        title="Tiến trình lượt khám"
        description="Theo dõi tiến trình và mở phiếu kết quả của lượt khám."
      />
      <form
        className="mb-4 flex flex-wrap items-end gap-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setSearchError("");
          if (!keyword.trim()) {
            setMatches([]);
            setPage(0);
            setFilter("");
            return;
          }
          setSearching(true);
          try {
            setMatches(await searchReceptionPatients(keyword));
          } catch (error) {
            setSearchError(messageFromError(error));
            setMatches([]);
          } finally {
            setSearching(false);
          }
        }}
      >
        <TextField
          label="Tên, số điện thoại hoặc mã bệnh nhân"
          value={keyword}
          onChange={setKeyword}
        />
        <Button type="submit" disabled={searching}>
          Tìm bệnh nhân
        </Button>
        <SelectField
          label="Trạng thái"
          value={status}
          onChange={(value) => {
            setPage(0);
            setStatus(value);
          }}
          options={[
            { value: "", label: "Tất cả" },
            { value: "OPEN", label: "Đang tiếp nhận hoặc khám" },
            { value: "CLOSED", label: "Đã kết thúc" },
            { value: "INCOMPLETE", label: "Chưa kết luận" },
          ]}
        />
      </form>
      {searchError && <p role="alert">{searchError}</p>}
      {matches.map((patient) => (
        <Button
          className="mr-2 mb-2"
          key={patient.patientProfileId}
          variant="outline"
          onClick={() => {
            setPage(0);
            setFilter(patient.patientProfileId);
            setMatches([]);
            setKeyword(patient.fullName);
          }}
        >
          {patient.fullName} · {patient.patientProfileId}
        </Button>
      ))}
      {filter && (
        <Button
          variant="outline"
          onClick={() => {
            setPage(0);
            setFilter("");
            setKeyword("");
          }}
        >
          Xem tất cả bệnh nhân
        </Button>
      )}
      {result.loading && <p>Đang tải lượt khám…</p>}
      {result.error != null && (
        <div role="alert">
          {messageFromError(result.error)}{" "}
          <Button variant="outline" onClick={result.refresh}>
            Thử lại
          </Button>
        </div>
      )}
      {result.data && (
        <>
          <DataTable
            label="Lượt khám"
            headers={[
              "Lượt khám",
              "Bệnh nhân",
              "Tiếp nhận",
              "Trạng thái",
              "Thao tác",
            ]}
            rows={result.data.items.map((v) => ({
              id: v.visitId,
              cells: [
                v.visitId,
                <PatientName
                  key="p"
                  name={v.patientName ?? "—"}
                  code={v.patientId}
                />,
                timeText(v.arrivedAt),
                labels[v.status] ?? v.status,
                <Button
                  key="view"
                  variant="outline"
                  onClick={() => setSelected(v.visitId)}
                >
                  Tiến trình
                </Button>,
              ],
            }))}
          />
          <div className="mt-4 flex gap-2">
            <Button
              variant="outline"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Trang trước
            </Button>
            <Button
              variant="outline"
              disabled={!result.data.hasMore}
              onClick={() => setPage(page + 1)}
            >
              Trang sau
            </Button>
          </div>
        </>
      )}
      {selected && (
        <VisitDetail visitId={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
