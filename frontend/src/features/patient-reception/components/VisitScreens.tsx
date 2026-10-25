"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Printer } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { onDuty, normalizeText, queueSort } from "../schema/receptionRules";
import { receptionDemoExpiry } from "../api/receptionApi";
import type { Visit, ResultSheet } from "../types";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  dateText,
  FormActions,
  FormModal,
  InfoRow,
  PatientName,
  RouteLink,
  SelectField,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

function TransferDoctor({
  visit,
  onClose,
}: {
  visit: Visit;
  onClose: () => void;
}) {
  const { data: s, run, pending } = useReception();
  const [doctorId, setDoctor] = useState("");
  const [reason, setReason] = useState("");
  return (
    <FormModal
      title="Chuyển bác sĩ phụ trách"
      description="Chuyển lượt khám khi bác sĩ phụ trách vắng. Bác sĩ mới sẽ nhận trách nhiệm và thông báo bàn giao."
      onClose={onClose}
    >
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          if (
            await run(
              { type: "transfer-doctor", visitId: visit.id, doctorId, reason },
              "Đã chuyển bác sĩ phụ trách và ghi nhận thông báo bàn giao (mô phỏng).",
            )
          )
            onClose();
        }}
      >
        <SelectField
          label="Bác sĩ đang trực"
          value={doctorId}
          onChange={setDoctor}
          disabled={pending}
          options={[
            { value: "", label: "Chọn bác sĩ trực" },
            ...onDuty(s!)
              .filter((d) => d.id !== visit.doctorId)
              .map((d) => ({
                value: d.id,
                label: `${d.name} · ${s!.rooms.find((r) => r.id === d.roomId)!.name}`,
              })),
          ]}
        />
        <TextField
          label="Lý do bàn giao *"
          value={reason}
          onChange={setReason}
          disabled={pending}
        />
        <FormActions
          onClose={onClose}
          label="Xác nhận bàn giao"
          disabled={!doctorId || reason.trim().length < 5}
        />
      </form>
    </FormModal>
  );
}
export function VisitDetail({
  visitId,
  onClose,
}: {
  visitId: string;
  onClose: () => void;
}) {
  const { data: s } = useReception();
  const [transfer, setTransfer] = useState(false);
  const visit = s!.visits.find((v) => v.id === visitId)!;
  const p = s!.patients.find((p) => p.id === visit.patientId)!;
  const doctor = s!.doctors.find((d) => d.id === visit.doctorId)!;
  const queues = s!.queues.filter((q) => q.visitId === visit.id);
  const orders = s!.orders.filter((o) => o.visitId === visit.id);
  if (transfer)
    return <TransferDoctor visit={visit} onClose={() => setTransfer(false)} />;
  return (
    <FormModal
      title={`Tiến trình ${visit.id}`}
      description={`${p.name} · ${p.id}. Màn hình này chỉ theo dõi tiến độ, không hiển thị nội dung kết quả.`}
      onClose={onClose}
      wide
    >
      <dl>
        <InfoRow label="Trạng thái">
          <Status value={visit.status} />
        </InfoRow>
        <InfoRow label="Bác sĩ phụ trách">
          {doctor.name}
          {doctor.absent && (
            <span className="ml-2 text-xs text-destructive">Vắng ca</span>
          )}
        </InfoRow>
        <InfoRow label="Tiếp nhận lần đầu">
          {dateText(visit.firstCheckIn)}
          {visit.status === "awaiting" && (
            <span className="block text-xs text-muted-foreground">
              Hạn tiếp tục: hết ngày {dateText(receptionDemoExpiry(visit))};
              không đặt lại khi bệnh nhân quay lại.
            </span>
          )}
        </InfoRow>
      </dl>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Hàng đợi qua các phòng</h2>
        {queues.length ? (
          <ul className="divide-y rounded-lg border">
            {queues.map((q) => {
              const list = s!.queues
                .filter(
                  (x) =>
                    x.roomId === q.roomId &&
                    x.kind === q.kind &&
                    x.date === q.date &&
                    x.status === "waiting",
                )
                .sort(queueSort);
              return (
                <li
                  key={q.id}
                  className="flex flex-wrap items-center justify-between gap-3 p-3"
                >
                  <div>
                    <p className="font-medium">
                      {s!.rooms.find((r) => r.id === q.roomId)!.name}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Số {q.number} ·{" "}
                      {q.kind === "review"
                        ? "Đọc kết quả"
                        : q.kind === "service"
                          ? "Dịch vụ"
                          : "Chờ khám"}{" "}
                      · {dateText(q.date)}
                      {q.status === "waiting"
                        ? ` · ${Math.max(
                            0,
                            list.findIndex((x) => x.id === q.id),
                          )} người phía trước`
                        : ""}
                    </p>
                  </div>
                  <Status
                    value={
                      q.calledAt && q.status === "waiting" ? "called" : q.status
                    }
                  />
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="text-muted-foreground">
            Chưa có hàng đợi đang lưu cho lượt này.
          </p>
        )}
      </section>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Chỉ định cận lâm sàng</h2>
        {orders.length ? (
          <ul className="divide-y rounded-lg border">
            {orders.map((o) => (
              <li
                key={o.id}
                className="flex flex-wrap items-center justify-between gap-3 p-3"
              >
                <div>
                  <p>{s!.services.find((v) => v.id === o.serviceId)!.name}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {o.id} · Đợt {o.round} ·{" "}
                    {s!.rooms.find((r) => r.id === o.roomId)!.name}
                  </p>
                  {o.cancellationReason && (
                    <p className="text-xs text-muted-foreground">
                      Lý do hủy: {o.cancellationReason}
                    </p>
                  )}
                </div>
                <Status value={o.status} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground">
            Bác sĩ chưa chỉ định dịch vụ cận lâm sàng.
          </p>
        )}
      </section>
      <div className="flex flex-wrap gap-2">
        {doctor.absent &&
          ["waiting", "examining", "awaiting"].includes(visit.status) && (
            <Button onClick={() => setTransfer(true)}>
              Chuyển bác sĩ trực
            </Button>
          )}
        <RouteLink
          href={`/reception/payments?patientId=${p.id}&contextId=${visit.id}`}
        >
          Các khoản thu
        </RouteLink>
        {visit.status === "awaiting" && (
          <RouteLink href={`/reception?patientId=${p.id}`}>
            Tiếp nhận quay lại
          </RouteLink>
        )}
      </div>
    </FormModal>
  );
}
export function VisitScreen() {
  const { data: s } = useReception();
  const params = useSearchParams();
  const initialId = params.get("visitId");
  const [selected, setSelected] = useState(
    s!.visits.some((v) => v.id === initialId) ? initialId : null,
  );
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const list = s!.visits.filter(
    (v) =>
      (!status || v.status === status) &&
      normalizeText(
        `${v.id} ${s!.patients.find((p) => p.id === v.patientId)!.name}`,
      ).includes(normalizeText(search)),
  );
  return (
    <>
      <PageHeader
        title="Tiến trình lượt khám"
        description="Theo dõi lượt khám đang hoạt động, lượt quay lại và trạng thái kết thúc."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <TextField
          label="Bệnh nhân / mã lượt khám"
          value={search}
          onChange={setSearch}
        />
        <SelectField
          label="Trạng thái lượt khám"
          value={status}
          onChange={setStatus}
          options={[
            { value: "", label: "Tất cả" },
            { value: "waiting", label: "Chờ khám" },
            { value: "examining", label: "Đang khám" },
            { value: "awaiting", label: "Chờ kết luận" },
            { value: "completed", label: "Hoàn thành" },
            { value: "incomplete", label: "Chưa kết luận · quá hạn" },
            { value: "left", label: "Đã rời phòng khám" },
          ]}
        />
      </div>
      <DataTable
        key={`${search}-${status}`}
        label="Lượt khám"
        headers={["Lượt khám", "Bệnh nhân", "Bác sĩ", "Trạng thái", "Thao tác"]}
        rows={list.map((v) => {
          const p = s!.patients.find((p) => p.id === v.patientId)!;
          return {
            id: v.id,
            cells: [
              <div key="visit">
                <p className="font-medium">{v.id}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {dateText(v.firstCheckIn)}
                </p>
              </div>,
              <PatientName key="patient" name={p.name} code={p.id} />,
              s!.doctors.find((d) => d.id === v.doctorId)!.name,
              <Status key="status" value={v.status} />,
              <Button
                key="details"
                variant="outline"
                onClick={() => setSelected(v.id)}
              >
                Tiến trình
              </Button>,
            ],
          };
        })}
      />
      {selected && (
        <VisitDetail visitId={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
export function OrderScreen() {
  const { data: s } = useReception();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [visitId, setVisitId] = useState("");
  const list = s!.orders.filter((o) => {
    const v = s!.visits.find((v) => v.id === o.visitId)!,
      p = s!.patients.find((p) => p.id === v.patientId)!;
    return (
      (!status || o.status === status) &&
      (!category ||
        s!.services.find((v) => v.id === o.serviceId)!.category === category) &&
      normalizeText(`${p.name} ${p.id} ${o.id} ${v.id}`).includes(
        normalizeText(search),
      )
    );
  });
  return (
    <>
      <PageHeader
        title="Trạng thái chỉ định"
        description="Xem tiến độ hình ảnh và xét nghiệm để hướng dẫn bệnh nhân đến đúng phòng."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <TextField
          label="Bệnh nhân / mã chỉ định / lượt khám"
          value={search}
          onChange={setSearch}
        />
        <SelectField
          label="Nhóm dịch vụ"
          value={category}
          onChange={setCategory}
          options={[
            { value: "", label: "Tất cả" },
            { value: "imaging", label: "Chẩn đoán hình ảnh" },
            { value: "lab", label: "Xét nghiệm" },
          ]}
        />
        <SelectField
          label="Trạng thái"
          value={status}
          onChange={setStatus}
          options={[
            { value: "", label: "Tất cả" },
            { value: "pending", label: "Chờ thanh toán" },
            { value: "paid", label: "Đã thanh toán" },
            { value: "results", label: "Đã có kết quả" },
            { value: "cancelled", label: "Đã hủy" },
          ]}
        />
      </div>
      <DataTable
        key={`${search}-${status}-${category}`}
        label="Chỉ định cận lâm sàng"
        headers={[
          "Chỉ định",
          "Bệnh nhân",
          "Dịch vụ",
          "Phòng",
          "Trạng thái",
          "Thao tác",
        ]}
        rows={list.map((o) => {
          const v = s!.visits.find((v) => v.id === o.visitId)!,
            p = s!.patients.find((p) => p.id === v.patientId)!;
          return {
            id: o.id,
            cells: [
              <div key="id">
                {o.id}
                <p className="text-xs text-muted-foreground">
                  {o.visitId} · đợt {o.round}
                </p>
              </div>,
              <PatientName key="patient" name={p.name} code={p.id} />,
              s!.services.find((v) => v.id === o.serviceId)!.name,
              s!.rooms.find((r) => r.id === o.roomId)!.name,
              <div key="status">
                <Status value={o.status} />
                {o.cancellationReason && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    {o.cancellationReason}
                  </p>
                )}
              </div>,
              <Button
                key="view"
                variant="outline"
                onClick={() => setVisitId(o.visitId)}
              >
                Xem tiến trình
              </Button>,
            ],
          };
        })}
      />
      {visitId && (
        <VisitDetail visitId={visitId} onClose={() => setVisitId("")} />
      )}
    </>
  );
}
function ResultPreview({
  sheet,
  onClose,
}: {
  sheet: ResultSheet;
  onClose: () => void;
}) {
  const { data: s } = useReception();
  const v = s!.visits.find((v) => v.id === sheet.visitId)!,
    p = s!.patients.find((p) => p.id === v.patientId)!;
  return (
    <FormModal
      title={`Phiếu kết quả ${sheet.id}`}
      description="Phiếu do hệ thống phát hành từ kết luận của bác sĩ. Lễ tân chỉ xem để in."
      onClose={onClose}
      wide
    >
      <article className="reception-print-document space-y-4 rounded-lg border p-5">
        <p className="font-semibold">
          OTCMS · Phòng khám chấn thương chỉnh hình
        </p>
        <h2 className="text-center text-lg font-semibold">
          PHIẾU KẾT QUẢ KHÁM
        </h2>
        <p className="text-center text-xs text-muted-foreground">
          Dữ liệu mẫu kiểm thử · không sử dụng làm kết quả khám thực tế
        </p>
        {sheet.replaced && (
          <p className="font-semibold text-destructive">
            PHIÊN BẢN ĐÃ ĐƯỢC THAY THẾ
          </p>
        )}
        <dl>
          <InfoRow label="Bệnh nhân">
            {p.name} · {p.id}
          </InfoRow>
          <InfoRow label="Ngày sinh / giới tính">
            {dateText(p.dob)} · {p.sex}
          </InfoRow>
          <InfoRow label="Lượt khám">{v.id}</InfoRow>
          <InfoRow label="Phát hành">
            {timeText(sheet.issuedAt)} · Phiên bản {sheet.version}
          </InfoRow>
        </dl>
        <section>
          <h3 className="font-semibold">Chẩn đoán</h3>
          <p className="mt-2">{sheet.diagnosis}</p>
        </section>
        <section>
          <h3 className="font-semibold">Kết luận</h3>
          <p className="mt-2">{sheet.conclusion}</p>
        </section>
        <section>
          <h3 className="font-semibold">Hướng dẫn sau khám</h3>
          <p className="mt-2">{sheet.advice}</p>
        </section>
        <p className="pt-4 text-right">Bác sĩ phụ trách: {sheet.doctorName}</p>
      </article>
      <div className="reception-no-print flex justify-end">
        <Button disabled={sheet.replaced} onClick={() => window.print()}>
          <Printer />
          In / lưu PDF
        </Button>
      </div>
    </FormModal>
  );
}
export function ResultScreen() {
  const { data: s } = useReception();
  const [search, setSearch] = useState("");
  const [sheet, setSheet] = useState<ResultSheet | null>(null);
  const list = s!.resultSheets.filter((sheet) => {
    const v = s!.visits.find((v) => v.id === sheet.visitId)!,
      p = s!.patients.find((p) => p.id === v.patientId)!;
    return normalizeText(`${p.name} ${p.id} ${sheet.id} ${v.id}`).includes(
      normalizeText(search),
    );
  });
  return (
    <>
      <PageHeader
        title="Phiếu kết quả khám"
        description="Chỉ in phiếu đã phát hành của lượt khám; không chỉnh sửa nội dung y khoa."
      />
      <div className="mb-4 max-w-lg">
        <TextField
          label="Tên bệnh nhân / mã phiếu / lượt khám"
          value={search}
          onChange={setSearch}
        />
      </div>
      <DataTable
        key={search}
        label="Phiếu kết quả khám"
        headers={["Phiếu", "Bệnh nhân", "Ngày phát hành", "Bác sĩ", "Thao tác"]}
        rows={list.map((r) => {
          const v = s!.visits.find((v) => v.id === r.visitId)!,
            p = s!.patients.find((p) => p.id === v.patientId)!;
          return {
            id: r.id,
            cells: [
              <div key="sheet">
                {r.id} · v{r.version}
                {r.replaced && (
                  <p className="text-xs text-destructive">Đã thay thế</p>
                )}
              </div>,
              <PatientName key="patient" name={p.name} code={p.id} />,
              timeText(r.issuedAt),
              r.doctorName,
              <Button key="print" variant="outline" onClick={() => setSheet(r)}>
                <Printer />
                Xem phiếu
              </Button>,
            ],
          };
        })}
        empty="Chưa có phiếu phù hợp. Phiếu được phát hành khi bác sĩ hoàn thành lượt khám."
      />
      {sheet && <ResultPreview sheet={sheet} onClose={() => setSheet(null)} />}
    </>
  );
}
