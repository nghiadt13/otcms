"use client";

import { useState, type FormEvent } from "react";
import { Plus, Search } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  normalizePhone,
  normalizeText,
  validatePatient,
} from "../schema/receptionRules";
import type { CareLink, Patient, PatientInput } from "../types";
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
}: {
  patient?: Patient;
  phone?: string;
  dob?: string;
  onClose: () => void;
}) {
  const { data, pending, run } = useReception();
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
    setInput((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };
  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validatePatient(input, data!.clock.slice(0, 10));
    setErrors(found);
    if (Object.keys(found).length) return;
    if (
      await run(
        {
          type: "save-patient",
          input,
          id: patient?.id,
          identityConfirmed: identity,
        },
        patient
          ? "Đã cập nhật thông tin hành chính."
          : "Đã tạo hồ sơ bệnh nhân. Chưa tạo tài khoản đăng nhập.",
      )
    )
      onClose();
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
            await run(
              {
                type: "confirm-link",
                id: link.id,
                evidence,
                identityConfirmed: checked,
              },
              "Đã xác minh quan hệ chăm sóc.",
            )
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
  const [phone, setPhone] = useState("");
  const [dob, setDob] = useState("");
  const [name, setName] = useState("");
  const [query, setQuery] = useState<{
    phone: string;
    dob: string;
    name: string;
  } | null>(null);
  const [error, setError] = useState("");
  const [create, setCreate] = useState(false);
  const [selected, setSelected] = useState<Patient | null>(null);
  const matches = query
    ? data!.patients.filter(
        (p) =>
          p.phone === query.phone &&
          p.dob === query.dob &&
          (!query.name ||
            normalizeText(p.name).includes(normalizeText(query.name))),
      )
    : [];
  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tìm bằng số điện thoại và ngày sinh; đối chiếu thêm họ tên nếu có nhiều hồ sơ khớp."
        actions={
          <Button disabled={!query || pending} onClick={() => setCreate(true)}>
            <Plus />
            Tạo hồ sơ
          </Button>
        }
      />
      <form
        className="mb-6 grid items-end gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2 xl:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (!/^0\d{9}$/.test(normalizePhone(phone)) || !dob) {
            setError(
              "Nhập số điện thoại hợp lệ và ngày sinh để tìm bệnh nhân.",
            );
            return;
          }
          setError("");
          setQuery({ phone: normalizePhone(phone), dob, name });
        }}
      >
        <TextField
          label="Số điện thoại"
          type="tel"
          value={phone}
          onChange={setPhone}
        />
        <TextField
          label="Ngày sinh"
          type="date"
          value={dob}
          onChange={setDob}
          max={data!.clock.slice(0, 10)}
        />
        <TextField
          label="Họ tên (khi cần phân biệt)"
          value={name}
          onChange={setName}
        />
        <Button variant="outline" type="submit">
          <Search />
          Tìm bệnh nhân
        </Button>
      </form>
      {error && (
        <p role="alert" className="mb-4 text-destructive">
          {error}
        </p>
      )}
      {matches.length > 1 && (
        <p className="mb-4 text-muted-foreground">
          Có nhiều hồ sơ khớp. Hãy hỏi họ tên đầy đủ và đối chiếu danh tính.
        </p>
      )}
      <DataTable
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
            : "Nhập số điện thoại và ngày sinh để bắt đầu tra cứu."
        }
      />
      {create && (
        <PatientForm
          phone={query?.phone}
          dob={query?.dob}
          onClose={() => setCreate(false)}
        />
      )}
      {selected && (
        <PatientDetails patient={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
