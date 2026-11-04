"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Plus, Search } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { isApiError, type MsgCode } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import {
  createReceptionProfile,
  getReceptionAdministrativeProfile,
  matchReceptionProfiles,
  searchReceptionPatients,
  updateReceptionProfile,
  type ReceptionAdministrativeProfile,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import { PendingCareLinks } from "./PendingCareLinks";
import { DataTable, dateText, FormModal, RouteLink } from "./ReceptionUi";

const profileErrors: Record<string, MsgCode> = {
  VALIDATION_FAILED: "MSG-17",
  PATIENT_PROFILE_DUPLICATE: "MSG-18",
  PATIENT_MATCH_AMBIGUOUS: "MSG-18",
  ACCESS_DENIED: "MSG-19",
  PATIENT_PROFILE_NOT_FOUND: "MSG-19",
  STALE_VERSION: "MSG-16",
};

function profileError(cause: unknown) {
  const code =
    isApiError(cause) && cause.errorCode
      ? profileErrors[cause.errorCode]
      : undefined;
  return code ? formatAppMessage(code) : messageFromError(cause);
}

type ProfileSelection = { mode: "create" } | { mode: "edit"; id: string };

export function PatientSearchScreen() {
  const [query, setQuery] = useState<{ keyword: string; dob: string } | null>(
    null,
  );
  const [rows, setRows] = useState<ReceptionPatient[] | null>(null);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<ProfileSelection | null>(null);

  useEffect(() => {
    if (!query) return;
    const controller = new AbortController();
    const signal = controller.signal;
    const request = query.dob
      ? matchReceptionProfiles(query.keyword, query.dob, signal)
      : searchReceptionPatients(query.keyword, signal);
    request.then(
      (patients) => {
        if (!signal.aborted) setRows(patients);
      },
      (cause: unknown) => {
        if (!signal.aborted) setError(profileError(cause));
      },
    );
    return () => controller.abort();
  }, [query]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setRows(null);
    setError("");
    const keyword = String(data.get("keyword") ?? "").trim();
    const dob = String(data.get("dob") ?? "");
    if (!keyword) {
      setQuery(null);
      setError(formatAppMessage("MSG-54"));
      return;
    }
    setQuery({ keyword, dob });
  }

  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tra cứu hồ sơ, đối chiếu thông tin hành chính và tạo hồ sơ cho người đến khám trực tiếp."
        actions={
          <Button onClick={() => setSelected({ mode: "create" })}>
            <Plus /> Tạo hồ sơ
          </Button>
        }
      />
      <form
        className="mb-4 flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4"
        onSubmit={search}
      >
        <label className="grid flex-1 gap-1.5">
          Tên hoặc số điện thoại
          <Input name="keyword" type="search" required autoComplete="off" />
        </label>
        <label className="grid gap-1.5">
          Ngày sinh
          <Input name="dob" type="date" />
        </label>
        <Button variant="outline" type="submit">
          <Search /> Tìm bệnh nhân
        </Button>
        <p className="basis-full text-sm text-muted-foreground">
          Để đối chiếu chính xác, nhập số điện thoại cùng ngày sinh.
        </p>
      </form>
      {error && (
        <p role="alert" className="mb-4 text-destructive">
          {error}
        </p>
      )}
      {query && rows === null && !error && <p role="status">Đang tìm hồ sơ…</p>}
      {rows && (
        <DataTable
          label="Kết quả tìm bệnh nhân"
          headers={["Bệnh nhân", "Ngày sinh", "Liên hệ", "Thao tác"]}
          rows={rows.map((patient) => ({
            id: patient.patientProfileId,
            cells: [
              patient.fullName,
              dateText(patient.dateOfBirth),
              patient.phoneCanonical ?? "—",
              <Button
                key="open"
                variant="outline"
                onClick={() =>
                  setSelected({ mode: "edit", id: patient.patientProfileId })
                }
              >
                Mở hồ sơ
              </Button>,
            ],
          }))}
          empty="Chưa tìm thấy hồ sơ phù hợp."
        />
      )}
      <PendingCareLinks />
      {selected && (
        <ReceptionProfileDialog
          key={selected.mode === "edit" ? selected.id : "create"}
          selection={selected}
          onClose={() => setSelected(null)}
          onSaved={(patient) => {
            setRows((previous) => [
              patient,
              ...(previous ?? []).filter(
                (row) => row.patientProfileId !== patient.patientProfileId,
              ),
            ]);
            setSelected({ mode: "edit", id: patient.patientProfileId });
          }}
        />
      )}
    </>
  );
}

function ReceptionProfileDialog({
  selection,
  onClose,
  onSaved,
}: {
  selection: ProfileSelection;
  onClose: () => void;
  onSaved: (patient: ReceptionPatient) => void;
}) {
  const id = selection.mode === "edit" ? selection.id : null;
  const [profile, setProfile] = useState<ReceptionAdministrativeProfile | null>(
    null,
  );
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const busy = useRef(false);
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    getReceptionAdministrativeProfile(id, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setProfile(detail);
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setError(profileError(cause));
      },
    );
    return () => controller.abort();
  }, [id]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current || (id && !profile)) return;
    const data = new FormData(event.currentTarget);
    const value = (name: string) => String(data.get(name) ?? "").trim();
    const nationalId = value("nationalId");
    const documentType = value("identityDocumentType");
    if (Boolean(nationalId) !== Boolean(documentType)) {
      setError("Chọn loại giấy tờ và nhập số giấy tờ tương ứng.");
      return;
    }
    const input = {
      fullName: value("fullName"),
      dateOfBirth: value("dateOfBirth"),
      phone: value("phone"),
      phoneOwnerType: value("phoneOwnerType"),
      gender: value("gender"),
      address: value("address") || null,
      nationalId: nationalId || null,
      identityDocumentType: documentType || null,
    };
    busy.current = true;
    setSaving(true);
    setError("");
    try {
      if (profile) {
        const updated = await updateReceptionProfile(
          profile.patientProfileId,
          profile.version,
          input,
        );
        setProfile(updated);
        onSaved({ ...updated, phoneCanonical: updated.phoneCanonical ?? "" });
      } else {
        onSaved(await createReceptionProfile(input));
      }
      notifySuccess("MSG-90");
    } catch (cause) {
      setError(profileError(cause));
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  const selectClass = "h-9 rounded-md border bg-background px-3";
  return (
    <FormModal
      title={id ? "Hồ sơ hành chính" : "Tạo hồ sơ khám trực tiếp"}
      description={
        id
          ? "Đối chiếu thông tin trước khi lưu."
          : "Hồ sơ được dùng để tiếp nhận người bệnh tại phòng khám."
      }
      onClose={() => {
        if (!busy.current) onClose();
      }}
    >
      {error && (
        <p role="alert" className="text-destructive">
          {error}
        </p>
      )}
      {id && !profile && !error && <p role="status">Đang tải hồ sơ…</p>}
      {(!id || profile) && (
        <form onSubmit={(event) => void submit(event)} className="space-y-4">
          <fieldset className="grid gap-3 sm:grid-cols-2" disabled={saving}>
            <label className="grid gap-1.5 sm:col-span-2">
              Họ và tên
              <Input
                name="fullName"
                required
                defaultValue={profile?.fullName}
              />
            </label>
            <label className="grid gap-1.5">
              Ngày sinh
              <Input
                name="dateOfBirth"
                type="date"
                required
                defaultValue={profile?.dateOfBirth}
              />
            </label>
            <label className="grid gap-1.5">
              Giới tính
              <select
                name="gender"
                className={selectClass}
                defaultValue={profile?.gender ?? ""}
                required
              >
                <option value="" disabled>
                  Chọn giới tính
                </option>
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
                <option value="OTHER">Khác</option>
              </select>
            </label>
            <label className="grid gap-1.5">
              Số điện thoại
              <Input
                name="phone"
                type="tel"
                required
                defaultValue={profile?.phoneCanonical ?? ""}
              />
            </label>
            <label className="grid gap-1.5">
              Số điện thoại của
              <select
                name="phoneOwnerType"
                className={selectClass}
                defaultValue={profile?.phoneOwnerType ?? "PATIENT"}
              >
                <option value="PATIENT">Người bệnh</option>
                <option value="GUARDIAN">Người giám hộ</option>
              </select>
            </label>
            <label className="grid gap-1.5 sm:col-span-2">
              Địa chỉ
              <Input
                name="address"
                required={!id}
                defaultValue={profile?.address ?? ""}
              />
            </label>
            <label className="grid gap-1.5">
              Loại giấy tờ
              <select
                name="identityDocumentType"
                className={selectClass}
                defaultValue={profile?.identityDocumentType ?? ""}
              >
                <option value="">Chưa cung cấp</option>
                <option value="CCCD">Căn cước công dân</option>
                <option value="PERSONAL_ID">Số định danh cá nhân</option>
                <option value="PASSPORT">Hộ chiếu</option>
              </select>
            </label>
            <label className="grid gap-1.5">
              Số giấy tờ
              <Input
                name="nationalId"
                defaultValue={profile?.nationalId ?? ""}
              />
            </label>
          </fieldset>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? "Đang lưu…" : "Lưu hồ sơ"}
            </Button>
            {profile && !saving && (
              <RouteLink
                href={`/reception?patientId=${encodeURIComponent(profile.patientProfileId)}`}
              >
                Tiếp nhận
              </RouteLink>
            )}
          </div>
        </form>
      )}
    </FormModal>
  );
}
