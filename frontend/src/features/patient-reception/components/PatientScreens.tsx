"use client";

import { useState, type FormEvent } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import { clinicDate, isDate } from "../schema/receptionRules";
import {
  createPatientProfile,
  getReceptionPatient,
  matchReceptionPatients,
  searchReceptionPatients,
  updatePatientProfile,
  type ReceptionPatient,
  type ReceptionPatientDetail,
  type ReceptionPatientInput,
} from "../api/receptionLiveApi";
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

const emptyInput: ReceptionPatientInput = {
  fullName: "",
  phone: "",
  phoneOwnerType: "PATIENT",
  dateOfBirth: "",
  gender: "MALE",
  address: "",
  nationalId: null,
  identityDocumentType: null,
  email: null,
  emailOwnerType: null,
};

function emailOwnerLabel(owner: ReceptionPatientDetail["emailOwnerType"]) {
  if (owner === "PATIENT") return "Bệnh nhân";
  if (owner === "RELATIVE") return "Người thân";
  return "";
}
function errorText(
  error: unknown,
  mode: "match" | "create" | "update" | "read",
) {
  if (isApiError(error)) {
    if (error.status === 400 || error.status === 422)
      return formatAppMessage("MSG-17");
    if (mode === "create" && error.status === 409 && error.code === "MSG-18")
      return "Hồ sơ bệnh nhân đã tồn tại. Tìm và mở hồ sơ hiện có.";
    if (
      (mode === "match" || mode === "create") &&
      error.status === 503 &&
      error.errorCode === "DEPENDENCY_UNAVAILABLE"
    )
      return "Hệ thống đang bận. Kiểm tra lại hồ sơ hiện có trước khi thử lưu lại.";
    if (error.status === 409 && error.code === "MSG-16")
      return formatAppMessage("MSG-16");
    if (
      (mode === "read" || mode === "update") &&
      (error.status === 403 || error.status === 404)
    )
      return formatAppMessage("MSG-19");
  }
  return messageFromError(error);
}

const fieldMessages = {
  fullName: "Nhập họ và tên từ 2 đến 100 ký tự.",
  dateOfBirth: "Nhập ngày sinh hợp lệ, không ở tương lai.",
  phone:
    "Nhập số điện thoại hợp lệ, gồm 8 đến 15 chữ số sau khi chuẩn hóa mã quốc gia.",
  phoneOwnerType: "Chọn chủ số điện thoại.",
  gender: "Chọn giới tính.",
  address: "Nhập địa chỉ liên hệ.",
  identityDocumentType: "Chọn loại giấy tờ tương ứng với số đã nhập.",
  nationalId: "Số định danh hoặc hộ chiếu tối đa 20 ký tự.",
  email: "Nhập địa chỉ email hợp lệ, ví dụ ten@example.com.",
  emailOwnerType: "Chọn chủ email của địa chỉ đã nhập.",
};

type ProfileFieldErrors = Partial<typeof fieldMessages>;

function validateProfile(input: ReceptionPatientInput, emailLocked: boolean) {
  const errors: ProfileFieldErrors = {};
  const name = input.fullName.trim().replace(/\s+/g, " ").normalize("NFC");
  const phone = input.phone
    .replace(/[\s().-]/g, "")
    .replace(/^00/, "+")
    .replace(/^0/, "+84")
    .replace(/^\+/, "");
  const email = input.email?.trim();
  const documentNumber = input.nationalId?.trim();

  if (name.length < 2 || name.length > 100)
    errors.fullName = fieldMessages.fullName;
  if (!isDate(input.dateOfBirth) || input.dateOfBirth > clinicDate())
    errors.dateOfBirth = fieldMessages.dateOfBirth;
  if (!/^\d{8,15}$/.test(phone)) errors.phone = fieldMessages.phone;
  if (!input.address.trim()) errors.address = fieldMessages.address;
  if (documentNumber && documentNumber.length > 20)
    errors.nationalId = fieldMessages.nationalId;
  if (documentNumber && !input.identityDocumentType)
    errors.identityDocumentType = fieldMessages.identityDocumentType;
  if (
    !emailLocked &&
    email &&
    (email.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
  )
    errors.email = fieldMessages.email;
  if (!emailLocked && email && !input.emailOwnerType)
    errors.emailOwnerType = fieldMessages.emailOwnerType;
  if (!emailLocked && !email && input.emailOwnerType)
    errors.email = "Nhập email hoặc bỏ chọn chủ email.";
  return errors;
}

function PatientForm({
  patient,
  onClose,
  onSaved,
}: {
  patient?: ReceptionPatientDetail;
  onClose: () => void;
  onSaved: (id: string) => void;
}) {
  const [input, setInput] = useState<ReceptionPatientInput>(
    patient
      ? {
          fullName: patient.fullName,
          phone: patient.phoneCanonical,
          phoneOwnerType: patient.phoneOwnerType,
          dateOfBirth: patient.dateOfBirth,
          gender: patient.gender,
          address: patient.address ?? "",
          nationalId: patient.nationalId,
          identityDocumentType: patient.identityDocumentType,
          email: patient.email ?? null,
          emailOwnerType: patient.emailOwnerType ?? null,
        }
      : emptyInput,
  );
  const emailLocked = Boolean(patient?.hasAccount);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [serverErrors, setServerErrors] = useState<ProfileFieldErrors>({});
  const fieldErrors = submitted
    ? { ...serverErrors, ...validateProfile(input, emailLocked) }
    : {};
  const [candidates, setCandidates] = useState<ReceptionPatient[]>([]);
  function change<K extends keyof ReceptionPatientInput>(
    key: K,
    value: ReceptionPatientInput[K],
  ) {
    setInput((prev) => ({ ...prev, [key]: value }));
    setError("");
    setServerErrors({});
    setCandidates([]);
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    const trimmedEmail = input.email?.trim() || null;
    const effectiveEmailOwner = trimmedEmail ? input.emailOwnerType : null;
    setSubmitted(true);
    setServerErrors({});
    setError("");
    if (Object.keys(validateProfile(input, emailLocked)).length) return;
    const payload: ReceptionPatientInput = {
      ...input,
      email: trimmedEmail,
      emailOwnerType: effectiveEmailOwner,
    };
    setPending(true);
    setError("");
    let mode: "match" | "create" | "update" = patient ? "update" : "match";
    try {
      if (patient) {
        const saved = await updatePatientProfile(
          patient.patientProfileId,
          payload,
          patient.version,
        );
        notifySuccess("MSG-84");
        onSaved(saved.patientProfileId);
        return;
      }
      const matches = await matchReceptionPatients(
        payload.phone,
        payload.dateOfBirth,
        payload.fullName,
      );
      if (matches.items.length) {
        setCandidates(matches.items);
        setError(formatAppMessage("MSG-18"));
        return;
      }
      mode = "create";
      const saved = await createPatientProfile(payload);
      notifySuccess("MSG-84");
      onSaved(saved.patientProfileId);
    } catch (cause) {
      const errors: ProfileFieldErrors = {};
      if (isApiError(cause) && (cause.status === 400 || cause.status === 422)) {
        const reported = toFieldErrorMap(cause.fieldErrors);
        for (const field of Object.keys(
          fieldMessages,
        ) as (keyof ProfileFieldErrors)[]) {
          if (
            reported[field] &&
            !(emailLocked && (field === "email" || field === "emailOwnerType"))
          )
            errors[field] = fieldMessages[field];
        }
      }
      setServerErrors(errors);
      if (!Object.keys(errors).length) setError(errorText(cause, mode));
    } finally {
      setPending(false);
    }
  }
  return (
    <FormModal
      title={patient ? "Cập nhật hồ sơ bệnh nhân" : "Tạo hồ sơ khám trực tiếp"}
      description="Đối chiếu số điện thoại, ngày sinh và họ tên để tránh tạo trùng hồ sơ."
      onClose={onClose}
      wide
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Họ và tên *"
            error={fieldErrors.fullName}
            value={input.fullName}
            onChange={(v) => change("fullName", v)}
            disabled={pending}
          />
          <TextField
            label="Ngày sinh *"
            type="date"
            error={fieldErrors.dateOfBirth}
            value={input.dateOfBirth}
            onChange={(v) => change("dateOfBirth", v)}
            disabled={pending}
          />
          <TextField
            label="Số điện thoại *"
            type="tel"
            error={fieldErrors.phone}
            value={input.phone}
            onChange={(v) => change("phone", v)}
            disabled={pending}
          />
          <SelectField
            label="Chủ số điện thoại"
            error={fieldErrors.phoneOwnerType}
            value={input.phoneOwnerType}
            onChange={(v) =>
              change(
                "phoneOwnerType",
                v as ReceptionPatientInput["phoneOwnerType"],
              )
            }
            options={[
              { value: "PATIENT", label: "Bệnh nhân" },
              { value: "GUARDIAN", label: "Người giám hộ" },
            ]}
            disabled={pending}
          />
          <SelectField
            label="Giới tính *"
            error={fieldErrors.gender}
            value={input.gender}
            onChange={(v) =>
              change("gender", v as ReceptionPatientInput["gender"])
            }
            options={[
              { value: "MALE", label: "Nam" },
              { value: "FEMALE", label: "Nữ" },
              { value: "OTHER", label: "Khác" },
            ]}
            disabled={pending}
          />
          <SelectField
            label="Loại giấy tờ"
            error={fieldErrors.identityDocumentType}
            value={input.identityDocumentType ?? ""}
            onChange={(v) =>
              change(
                "identityDocumentType",
                (v || null) as ReceptionPatientInput["identityDocumentType"],
              )
            }
            options={[
              { value: "", label: "Không có" },
              { value: "CCCD", label: "CCCD" },
              { value: "PERSONAL_ID", label: "Mã định danh cá nhân" },
              { value: "PASSPORT", label: "Hộ chiếu" },
            ]}
            disabled={pending}
          />
          <TextField
            label="Số định danh / hộ chiếu"
            error={fieldErrors.nationalId}
            value={input.nationalId ?? ""}
            onChange={(v) => change("nationalId", v || null)}
            disabled={pending}
          />
          <TextField
            label="Địa chỉ *"
            error={fieldErrors.address}
            value={input.address}
            onChange={(v) => change("address", v)}
            disabled={pending}
          />
          <TextField
            label="Email"
            type="email"
            error={fieldErrors.email}
            value={input.email ?? ""}
            onChange={(v) => {
              const nextEmail = v || null;
              setInput((prev) => ({
                ...prev,
                email: nextEmail,
                emailOwnerType: nextEmail ? prev.emailOwnerType : null,
              }));
              setError("");
              setServerErrors({});
              setCandidates([]);
            }}
            disabled={pending || emailLocked}
          />
          <SelectField
            label="Chủ email"
            error={fieldErrors.emailOwnerType}
            value={input.emailOwnerType ?? ""}
            onChange={(v) =>
              change(
                "emailOwnerType",
                (v || null) as ReceptionPatientInput["emailOwnerType"],
              )
            }
            options={[
              { value: "", label: "Không có" },
              { value: "PATIENT", label: "Bệnh nhân" },
              { value: "RELATIVE", label: "Người thân" },
            ]}
            disabled={pending || emailLocked}
          />
        </div>
        {emailLocked && (
          <p className="text-xs text-muted-foreground">
            Hồ sơ đã liên kết tài khoản nên không thể thay đổi email và chủ
            email.
          </p>
        )}
        {error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
        {candidates.map((p) => (
          <Button
            key={p.patientProfileId}
            type="button"
            variant="outline"
            onClick={() => onSaved(p.patientProfileId)}
          >
            Dùng hồ sơ {p.fullName} · {dateText(p.dateOfBirth)}
          </Button>
        ))}
        <FormActions
          onClose={onClose}
          disabled={pending}
          label={patient ? "Lưu thay đổi" : "Tạo hồ sơ"}
        />
      </form>
    </FormModal>
  );
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
    setPending(true);
    setError("");
    try {
      setDetail(await getReceptionPatient(id));
      setCreate(false);
      setEditing(false);
    } catch (cause) {
      setError(errorText(cause, "read"));
    } finally {
      setPending(false);
    }
  }
  async function search(e: FormEvent) {
    e.preventDefault();
    if (!keyword.trim()) {
      setError(formatAppMessage("MSG-17"));
      return;
    }
    setPending(true);
    setError("");
    try {
      const found = await searchReceptionPatients(keyword);
      setResults(dob ? found.filter((p) => p.dateOfBirth === dob) : found);
    } catch (cause) {
      setError(errorText(cause, "read"));
      setResults(null);
    } finally {
      setPending(false);
    }
  }
  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tìm và đối chiếu thông tin bệnh nhân."
        actions={
          <Button disabled={pending} onClick={() => setCreate(true)}>
            Tạo hồ sơ
          </Button>
        }
      />
      <form
        onSubmit={search}
        className="mb-4 grid items-end gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2"
      >
        <TextField
          label="Tên, số điện thoại hoặc mã bệnh nhân"
          value={keyword}
          onChange={setKeyword}
        />
        <TextField
          label="Ngày sinh (không bắt buộc)"
          type="date"
          value={dob}
          onChange={setDob}
        />
        <Button type="submit" disabled={pending}>
          Tìm bệnh nhân
        </Button>
      </form>
      {error && (
        <p role="alert" className="text-destructive">
          {error}
        </p>
      )}
      {results && <p role="status">{results.length} hồ sơ phù hợp.</p>}
      <DataTable
        label="Kết quả tìm bệnh nhân"
        headers={["Bệnh nhân", "Ngày sinh", "Liên hệ", "Thao tác"]}
        rows={(results ?? []).map((p) => ({
          id: p.patientProfileId,
          cells: [
            <PatientName
              key="name"
              name={p.fullName}
              code={p.patientProfileId}
            />,
            dateText(p.dateOfBirth),
            p.phoneCanonical,
            <Button
              key="open"
              variant="outline"
              onClick={() => void open(p.patientProfileId)}
            >
              Mở hồ sơ
            </Button>,
          ],
        }))}
        empty="Nhập thông tin để tìm hồ sơ bệnh nhân."
      />
      {create && (
        <PatientForm
          onClose={() => setCreate(false)}
          onSaved={(id) => void open(id)}
        />
      )}
      {detail &&
        (editing ? (
          <PatientForm
            patient={detail}
            onClose={() => setEditing(false)}
            onSaved={(id) => void open(id)}
          />
        ) : (
          <FormModal
            title={detail.fullName}
            description="Thông tin hành chính phục vụ tiếp nhận."
            onClose={() => setDetail(null)}
            wide
          >
            <dl>
              <InfoRow label="Ngày sinh">
                {dateText(detail.dateOfBirth)}
              </InfoRow>
              <InfoRow label="Giới tính">
                {detail.gender === "MALE"
                  ? "Nam"
                  : detail.gender === "FEMALE"
                    ? "Nữ"
                    : "Khác"}
              </InfoRow>
              <InfoRow label="Số điện thoại">{detail.phoneCanonical}</InfoRow>
              <InfoRow label="Email">
                {detail.email
                  ? detail.emailOwnerType
                    ? `${detail.email} (${emailOwnerLabel(detail.emailOwnerType)})`
                    : detail.email
                  : "Chưa cung cấp"}
              </InfoRow>
              <InfoRow label="Địa chỉ">
                {detail.address || "Chưa cung cấp"}
              </InfoRow>
              <InfoRow label="Định danh">
                {detail.nationalId || "Chưa cung cấp"}
              </InfoRow>
            </dl>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setEditing(true)}>Sửa thông tin</Button>
              <RouteLink
                href={`/reception?patientId=${detail.patientProfileId}`}
              >
                Tiếp nhận
              </RouteLink>
            </div>
          </FormModal>
        ))}
    </>
  );
}
