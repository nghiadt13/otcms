"use client";

import { useState } from "react";
import { Printer } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { normalizeText } from "../schema/receptionRules";
import type { ResultSheet } from "../types";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  dateText,
  FormModal,
  InfoRow,
  PatientName,
  SelectField,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

export { VisitDetail, VisitScreen } from "./LiveVisitScreens";
import { VisitDetail } from "./LiveVisitScreens";
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
