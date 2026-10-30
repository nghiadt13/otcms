"use client";

import { useState, type FormEvent } from "react";
import { Plus, Search } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  normalizePhone,
  matchesPatientSearch,
  isDate,
  validatePatient,
} from "../schema/receptionRules";
import type {
  CareLink,
  Patient,
  PatientInput,
  PatientSearchQuery,
} from "../types";
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
  TextField,
} from "./ReceptionUi";

export function PatientForm({
  patient,
  phone,
  dob,
  onClose,
  onSaved,
}: {
  patient?: Patient;
  phone?: string;
  dob?: string;
  onClose: () => void;
  onSaved?: (patient: Patient) => void;
}) {
  const { data, pending, run, error } = useReception();
  const [saveAttempted, setSaveAttempted] = useState(false);
  const [input, setInput] = useState<PatientInput>(
    patient ?? {
      name: "",
      phone: phone ?? "",
      dob: dob ?? "",
      sex: "Nam",
      address: "",
      nationalId: "",
      email: "",
    },
  );
  const [errors, setErrors] = useState<
    Partial<Record<keyof PatientInput, string>>
  >({});
  const [identity, setIdentity] = useState(false);
  const change = (key: keyof PatientInput, value: string) => {
    setSaveAttempted(false);
    setInput((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };
  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validatePatient(input, data!.clock.slice(0, 10));
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaveAttempted(true);
    const next = await run({
      type: "save-patient",
      input,
      id: patient?.id,
      identityConfirmed: identity,
    });
    if (next) {
      const saved = patient
        ? next.patients.find((p) => p.id === patient.id)
        : next.patients[0];
      if (saved) onSaved?.(saved);
      onClose();
    }
  }
  return (
    <FormModal
      title={patient ? "Cập nhật hồ sơ bệnh nhân" : "Tạo hồ sơ khám trực tiếp"}
      description="Đối chiếu số điện thoại, ngày sinh và họ tên để tránh tạo trùng hồ sơ."
      onClose={onClose}
      wide
    >
      <form onSubmit={submit} noValidate className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Họ và tên *"
            value={input.name}
            onChange={(v) => change("name", v)}
            error={errors.name}
            disabled={pending}
          />
          <TextField
            label="Ngày sinh *"
            type="date"
            value={input.dob}
            onChange={(v) => change("dob", v)}
            max={data!.clock.slice(0, 10)}
            error={errors.dob}
            disabled={pending}
          />
          <TextField
            label="Số điện thoại *"
            type="tel"
            value={input.phone}
            onChange={(v) => change("phone", v)}
            error={errors.phone}
            disabled={pending}
          />
          <SelectField
            label="Giới tính *"
            value={input.sex}
            onChange={(v) => change("sex", v)}
            options={["Nam", "Nữ", "Khác"].map((v) => ({ value: v, label: v }))}
            disabled={pending}
          />
          <TextField
            label="Số định danh / hộ chiếu"
            value={input.nationalId}
            onChange={(v) => change("nationalId", v)}
            error={errors.nationalId}
            disabled={pending}
            hint="Không bắt buộc."
          />
          <TextField
            label="Email liên hệ"
            type="email"
            value={input.email}
            onChange={(v) => change("email", v)}
            error={errors.email}
            disabled={pending}
          />
        </div>
        <TextField
          label="Địa chỉ *"
          value={input.address}
          onChange={(v) => change("address", v)}
          error={errors.address}
          disabled={pending}
        />
        <label className="flex items-start gap-3">
          <Checkbox
            checked={identity}
            onCheckedChange={setIdentity}
            disabled={pending}
          />
          <span>
            Đã đối chiếu danh tính và kiểm tra hồ sơ khớp trước khi lưu.
          </span>
        </label>
        {saveAttempted && error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
        <FormActions
          onClose={onClose}
          disabled={!identity}
          label={patient ? "Lưu thay đổi" : "Tạo hồ sơ"}
        />
      </form>
    </FormModal>
  );
}
function CareConfirmation({
  link,
  onClose,
}: {
  link: CareLink;
  onClose: () => void;
}) {
  const { data, run, pending } = useReception();
  const [evidence, setEvidence] = useState("");
  const [checked, setChecked] = useState(false);
  return (
    <FormModal
      title="Xác minh quan hệ chăm sóc"
      description="Chỉ xác nhận sau khi đối chiếu giấy tờ tại quầy. Việc liên kết cấp quyền xem thông tin và thanh toán cho người được chăm sóc."
      onClose={onClose}
    >
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          if (
            await run({
              type: "confirm-link",
              id: link.id,
              evidence,
              identityConfirmed: checked,
            })
          )
            onClose();
        }}
      >
        <dl>
          <InfoRow label="Bệnh nhân">
            {data!.patients.find((p) => p.id === link.patientId)?.name}
          </InfoRow>
          <InfoRow label="Tài khoản yêu cầu">
            {link.accountName} · {link.accountEmail}
          </InfoRow>
          <InfoRow label="Quan hệ">{link.relationship}</InfoRow>
        </dl>
        <TextField
          label="Căn cứ xác minh *"
          hint="Ghi loại giấy tờ đã đối chiếu và kết quả; không chép toàn bộ số giấy tờ."
          value={evidence}
          onChange={setEvidence}
          disabled={pending}
        />
        <label className="flex items-start gap-3">
          <Checkbox
            checked={checked}
            onCheckedChange={setChecked}
            disabled={pending}
          />
          <span>Đã đối chiếu đúng người và giấy tờ chứng minh quan hệ.</span>
        </label>
        <FormActions
          onClose={onClose}
          label="Xác nhận liên kết"
          disabled={!checked || evidence.trim().length < 5}
        />
      </form>
    </FormModal>
  );
}
export function PatientDetails({
  patient,
  onClose,
}: {
  patient: Patient;
  onClose: () => void;
}) {
  const { data } = useReception();
  const [editing, setEditing] = useState(false);
  const [link, setLink] = useState<CareLink | null>(null);
  const current = data!.patients.find((p) => p.id === patient.id) ?? patient;
  if (editing)
    return <PatientForm patient={current} onClose={() => setEditing(false)} />;
  if (link)
    return <CareConfirmation link={link} onClose={() => setLink(null)} />;
  return (
    <FormModal
      title={current.name}
      description={`${current.id} · Chỉ hiển thị thông tin hành chính phục vụ tiếp nhận.`}
      onClose={onClose}
      wide
    >
      <dl>
        <InfoRow label="Ngày sinh / giới tính">
          {dateText(current.dob)} · {current.sex}
        </InfoRow>
        <InfoRow label="Số điện thoại">{current.phone}</InfoRow>
        <InfoRow label="Địa chỉ">{current.address}</InfoRow>
        <InfoRow label="Định danh">
          {current.nationalId || "Chưa cung cấp"}
        </InfoRow>
        <InfoRow label="Email">{current.email || "Chưa cung cấp"}</InfoRow>
      </dl>
      {data!.careLinks
        .filter((l) => l.patientId === current.id)
        .map((l) => (
          <div
            key={l.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
          >
            <div>
              <p className="font-medium">
                {l.accountName} · {l.relationship}
              </p>
              <p className="text-xs text-muted-foreground">
                {l.status === "confirmed"
                  ? "Đã xác minh quan hệ"
                  : "Chờ xác minh giấy tờ tại quầy"}
              </p>
            </div>
            {l.status === "pending" && (
              <Button variant="outline" onClick={() => setLink(l)}>
                Xác minh
              </Button>
            )}
          </div>
        ))}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setEditing(true)}>Sửa thông tin</Button>
        <RouteLink href={`/reception?patientId=${current.id}`}>
          Tiếp nhận
        </RouteLink>
        <RouteLink href={`/reception/appointments?patientId=${current.id}`}>
          Đặt lịch
        </RouteLink>
        <RouteLink href={`/reception/payments?patientId=${current.id}`}>
          Khoản thu
        </RouteLink>
      </div>
    </FormModal>
  );
}
export function PatientSearchScreen() {
  const { data, pending } = useReception();
  const [keyword, setKeyword] = useState("");
  const [dob, setDob] = useState("");
  const [query, setQuery] = useState<PatientSearchQuery | null>(null);
  const [error, setError] = useState("");
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<Patient | null>(null);
  const matches = query
    ? data!.patients.filter((p) => matchesPatientSearch(p, query))
    : [];
  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tìm bằng tên, số điện thoại hoặc mã bệnh nhân; đối chiếu thông tin trước khi chọn hồ sơ."
        actions={
          <Button disabled={pending} onClick={() => setCreate(true)}>
            <Plus />
            Tạo hồ sơ
          </Button>
        }
      />
      <form
        className="mb-4 grid items-end gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_14rem_auto]"
        onSubmit={(e) => {
          e.preventDefault();
          if (
            !/[\p{L}\p{N}]/u.test(keyword) ||
            (dob && (!isDate(dob) || dob > data!.clock.slice(0, 10)))
          ) {
            setError(formatAppMessage("MSG-54"));
            setQuery(null);
            return;
          }
          setError("");
          setQuery({ q: keyword.trim(), dob: dob || undefined });
        }}
      >
        <TextField
          label="Tên, số điện thoại hoặc mã bệnh nhân"
          type="search"
          value={keyword}
          onChange={setKeyword}
          autoComplete="off"
        />
        <TextField
          label="Ngày sinh (không bắt buộc)"
          type="date"
          value={dob}
          onChange={setDob}
          max={data!.clock.slice(0, 10)}
        />
        <Button variant="outline" type="submit">
          <Search />
          Tìm bệnh nhân
        </Button>
      </form>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
        <p>Có thể nhập tên không dấu, ví dụ: Binh, 0900000002 hoặc BN0002.</p>
        {(keyword || dob || query) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setKeyword("");
              setDob("");
              setQuery(null);
              setError("");
            }}
          >
            Xóa bộ lọc
          </Button>
        )}
      </div>
      {error && (
        <p role="alert" className="mb-4 text-destructive">
          {error}
        </p>
      )}
      {query && (
        <p role="status" className="mb-4 text-muted-foreground">
          {matches.length} hồ sơ phù hợp. Đối chiếu họ tên, ngày sinh và số điện
          thoại trước khi chọn.
        </p>
      )}
      <DataTable
        key={`${query?.q ?? ""}-${query?.dob ?? ""}`}
        label="Kết quả tìm bệnh nhân"
        headers={["Bệnh nhân", "Ngày sinh", "Liên hệ", "Thao tác"]}
        rows={matches.map((p) => ({
          id: p.id,
          cells: [
            <PatientName key="name" name={p.name} code={p.id} />,
            dateText(p.dob),
            p.phone,
            <Button key="open" variant="outline" onClick={() => setSelected(p)}>
              Mở hồ sơ
            </Button>,
          ],
        }))}
        empty={
          query
            ? "Chưa có hồ sơ khớp. Kiểm tra lại thông tin trước khi tạo hồ sơ mới."
            : "Nhập tên, số điện thoại hoặc mã bệnh nhân để tìm hồ sơ. Bạn cũng có thể tạo hồ sơ mới."
        }
      />
      {create && (
        <PatientForm
          phone={
            /^0\d{9}$/.test(normalizePhone(keyword.trim()))
              ? normalizePhone(keyword.trim())
              : undefined
          }
          dob={isDate(dob) && dob <= data!.clock.slice(0, 10) ? dob : undefined}
          onClose={() => setCreate(false)}
          onSaved={(patient) => {
            setKeyword(patient.id);
            setDob("");
            setError("");
            setQuery({ q: patient.id });
          }}
        />
      )}
      {selected && (
        <PatientDetails patient={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
