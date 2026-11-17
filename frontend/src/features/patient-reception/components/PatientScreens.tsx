"use client";

import { useState, type FormEvent } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import { createPatientProfile, getReceptionPatient, matchReceptionPatients, searchReceptionPatients, updatePatientProfile, type ReceptionPatient, type ReceptionPatientDetail, type ReceptionPatientInput } from "../api/receptionLiveApi";
import { DataTable, dateText, FormActions, FormModal, InfoRow, PatientName, RouteLink, SelectField, TextField } from "./ReceptionUi";

const emptyInput: ReceptionPatientInput = { fullName: "", phone: "", phoneOwnerType: "PATIENT", dateOfBirth: "", gender: "MALE", address: "", nationalId: null, identityDocumentType: null };
function errorText(error: unknown, mode: "create" | "update" | "read") {
  if (isApiError(error)) {
    if (error.status === 400 || error.status === 422) return formatAppMessage("MSG-17");
    if (error.status === 409 && mode === "create") return formatAppMessage("MSG-18");
    if (error.status === 409 && error.errorCode === "STALE_VERSION") return formatAppMessage("MSG-16");
    if ((error.status === 403 || error.status === 404) && mode !== "create") return formatAppMessage("MSG-19");
  }
  return messageFromError(error);
}

function PatientForm({ patient, onClose, onSaved }: { patient?: ReceptionPatientDetail; onClose: () => void; onSaved: (id: string) => void }) {
  const [input, setInput] = useState<ReceptionPatientInput>(patient ? { fullName: patient.fullName, phone: patient.phoneCanonical, phoneOwnerType: patient.phoneOwnerType, dateOfBirth: patient.dateOfBirth, gender: patient.gender, address: patient.address ?? "", nationalId: patient.nationalId, identityDocumentType: patient.identityDocumentType } : emptyInput);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [candidates, setCandidates] = useState<ReceptionPatient[]>([]);
  function change<K extends keyof ReceptionPatientInput>(key: K, value: ReceptionPatientInput[K]) { setInput((prev) => ({ ...prev, [key]: value })); setError(""); setCandidates([]); }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!input.fullName.trim() || !input.phone.trim() || !input.dateOfBirth || !input.address.trim() || (input.nationalId && !input.identityDocumentType)) { setError(formatAppMessage("MSG-17")); return; }
    setPending(true); setError("");
    try {
      if (patient) {
        const saved = await updatePatientProfile(patient.patientProfileId, input, patient.version);
        notifySuccess("MSG-84"); onSaved(saved.patientProfileId); return;
      }
      const matches = await matchReceptionPatients(input.phone, input.dateOfBirth, input.fullName);
      if (matches.items.length) { setCandidates(matches.items); setError(formatAppMessage("MSG-18")); return; }
      const saved = await createPatientProfile(input);
      notifySuccess("MSG-84"); onSaved(saved.patientProfileId);
    } catch (cause) { setError(errorText(cause, patient ? "update" : "create")); }
    finally { setPending(false); }
  }
  return <FormModal title={patient ? "Cập nhật hồ sơ bệnh nhân" : "Tạo hồ sơ khám trực tiếp"} description="Đối chiếu số điện thoại, ngày sinh và họ tên để tránh tạo trùng hồ sơ." onClose={onClose} wide>
    <form onSubmit={submit} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Họ và tên *" value={input.fullName} onChange={(v) => change("fullName", v)} disabled={pending} />
        <TextField label="Ngày sinh *" type="date" value={input.dateOfBirth} onChange={(v) => change("dateOfBirth", v)} disabled={pending} />
        <TextField label="Số điện thoại *" type="tel" value={input.phone} onChange={(v) => change("phone", v)} disabled={pending} />
        <SelectField label="Chủ số điện thoại" value={input.phoneOwnerType} onChange={(v) => change("phoneOwnerType", v as ReceptionPatientInput["phoneOwnerType"])} options={[{value:"PATIENT",label:"Bệnh nhân"},{value:"GUARDIAN",label:"Người giám hộ"}]} disabled={pending} />
        <SelectField label="Giới tính *" value={input.gender} onChange={(v) => change("gender", v as ReceptionPatientInput["gender"])} options={[{value:"MALE",label:"Nam"},{value:"FEMALE",label:"Nữ"},{value:"OTHER",label:"Khác"}]} disabled={pending} />
        <SelectField label="Loại giấy tờ" value={input.identityDocumentType ?? ""} onChange={(v) => change("identityDocumentType", (v || null) as ReceptionPatientInput["identityDocumentType"])} options={[{value:"",label:"Không có"},{value:"CCCD",label:"CCCD"},{value:"PERSONAL_ID",label:"Mã định danh cá nhân"},{value:"PASSPORT",label:"Hộ chiếu"}]} disabled={pending} />
        <TextField label="Số định danh / hộ chiếu" value={input.nationalId ?? ""} onChange={(v) => change("nationalId", v || null)} disabled={pending} />
        <TextField label="Địa chỉ *" value={input.address} onChange={(v) => change("address", v)} disabled={pending} />
      </div>
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {candidates.map((p) => <Button key={p.patientProfileId} type="button" variant="outline" onClick={() => onSaved(p.patientProfileId)}>Dùng hồ sơ {p.fullName} · {dateText(p.dateOfBirth)}</Button>)}
      <FormActions onClose={onClose} disabled={pending} label={patient ? "Lưu thay đổi" : "Tạo hồ sơ"} />
    </form>
  </FormModal>;
}

export function PatientSearchScreen() {
  const [keyword, setKeyword] = useState("");
  const [dob, setDob] = useState("");
  const [results, setResults] = useState<ReceptionPatient[] | null>(null);
  const [detail, setDetail] = useState<ReceptionPatientDetail | null>(null);
  const [create, setCreate] = useState(false);
  const [editing, setEditing] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function open(id: string) {
    setPending(true); setError("");
    try { setDetail(await getReceptionPatient(id)); setCreate(false); setEditing(false); }
    catch (cause) { setError(errorText(cause, "read")); }
    finally { setPending(false); }
  }
  async function search(e: FormEvent) {
    e.preventDefault();
    if (!keyword.trim()) { setError(formatAppMessage("MSG-17")); return; }
    setPending(true); setError("");
    try { const found = await searchReceptionPatients(keyword); setResults(dob ? found.filter((p) => p.dateOfBirth === dob) : found); }
    catch (cause) { setError(errorText(cause, "read")); setResults(null); }
    finally { setPending(false); }
  }
  return <>
    <PageHeader title="Hồ sơ bệnh nhân" description="Tìm và đối chiếu thông tin bệnh nhân." actions={<Button disabled={pending} onClick={() => setCreate(true)}>Tạo hồ sơ</Button>} />
    <form onSubmit={search} className="mb-4 grid items-end gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2">
      <TextField label="Tên, số điện thoại hoặc mã bệnh nhân" value={keyword} onChange={setKeyword} />
      <TextField label="Ngày sinh (không bắt buộc)" type="date" value={dob} onChange={setDob} />
      <Button type="submit" disabled={pending}>Tìm bệnh nhân</Button>
    </form>
    {error && <p role="alert" className="text-destructive">{error}</p>}
    {results && <p role="status">{results.length} hồ sơ phù hợp.</p>}
    <DataTable label="Kết quả tìm bệnh nhân" headers={["Bệnh nhân", "Ngày sinh", "Liên hệ", "Thao tác"]} rows={(results ?? []).map((p) => ({id:p.patientProfileId,cells:[<PatientName key="name" name={p.fullName} code={p.patientProfileId} />,dateText(p.dateOfBirth),p.phoneCanonical,<Button key="open" variant="outline" onClick={() => void open(p.patientProfileId)}>Mở hồ sơ</Button>]}))} empty="Nhập thông tin để tìm hồ sơ bệnh nhân." />
    {create && <PatientForm onClose={() => setCreate(false)} onSaved={(id) => void open(id)} />}
    {detail && (editing ? <PatientForm patient={detail} onClose={() => setEditing(false)} onSaved={(id) => void open(id)} /> : <FormModal title={detail.fullName} description="Thông tin hành chính phục vụ tiếp nhận." onClose={() => setDetail(null)} wide><dl><InfoRow label="Ngày sinh">{dateText(detail.dateOfBirth)}</InfoRow><InfoRow label="Giới tính">{detail.gender === "MALE" ? "Nam" : detail.gender === "FEMALE" ? "Nữ" : "Khác"}</InfoRow><InfoRow label="Số điện thoại">{detail.phoneCanonical}</InfoRow><InfoRow label="Địa chỉ">{detail.address || "Chưa cung cấp"}</InfoRow><InfoRow label="Định danh">{detail.nationalId || "Chưa cung cấp"}</InfoRow></dl><div className="flex flex-wrap gap-2"><Button onClick={() => setEditing(true)}>Sửa thông tin</Button><RouteLink href={`/reception?patientId=${detail.patientProfileId}`}>Tiếp nhận</RouteLink></div></FormModal>)}
  </>;
}
