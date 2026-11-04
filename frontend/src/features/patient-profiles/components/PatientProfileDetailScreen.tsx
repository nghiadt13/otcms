"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { notifySuccess } from "@/lib/messages/notify";
import {
  dependentErrorMessage,
  getAdministrativeProfile,
  listManagedProfiles,
  removeDependent,
  updateAdministrativeProfile,
  type AdministrativeProfile,
} from "../api/dependentApi";
import { PortalLoadError } from "./PortalLoadError";

export function PatientProfileDetailScreen({
  profileId,
}: {
  profileId: string;
}) {
  return <ProfileDetail key={profileId} profileId={profileId} />;
}

function ProfileDetail({ profileId }: { profileId: string }) {
  const router = useRouter();
  const [detail, setDetail] = useState<{
    profile: AdministrativeProfile;
    own: boolean;
  } | null>(null);
  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const busy = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    const signal = controller.signal;
    async function load() {
      try {
        const { items } = await listManagedProfiles(signal);
        if (signal.aborted) return;
        const entry = items.find(
          (item) =>
            item.patientProfileId === profileId &&
            item.linkStatus === "CONFIRMED",
        );
        if (!entry) {
          setLoadError("Không thể quản lý hồ sơ này.");
          return;
        }
        const own = entry.relationship === "SELF";
        const profile = await getAdministrativeProfile(profileId, own, signal);
        if (!signal.aborted) setDetail({ profile, own });
      } catch (cause) {
        if (!signal.aborted) setLoadError(dependentErrorMessage(cause));
      }
    }
    void load();
    return () => controller.abort();
  }, [profileId, attempt]);

  async function run(action: () => Promise<void>) {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      await action();
    } catch (cause) {
      setError(dependentErrorMessage(cause));
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  if (loadError)
    return (
      <PortalLoadError
        title="Không tải được hồ sơ."
        message={loadError}
        onRetry={() => {
          setLoadError("");
          setAttempt((value) => value + 1);
        }}
      />
    );
  if (!detail) return <p role="status">Đang tải hồ sơ…</p>;

  const { profile, own } = detail;

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value = (key: string) => String(data.get(key) ?? "").trim();
    const nationalId = value("nationalId");
    const identityDocumentType = value("identityDocumentType");
    if (Boolean(nationalId) !== Boolean(identityDocumentType)) {
      setError("Chọn loại giấy tờ và nhập số giấy tờ tương ứng.");
      return;
    }
    void run(async () => {
      const updated = await updateAdministrativeProfile(profile, own, {
        fullName: value("fullName"),
        dateOfBirth: value("dateOfBirth"),
        gender: value("gender"),
        phone: value("phone") || null,
        address: value("address") || null,
        nationalId: nationalId || null,
        identityDocumentType: identityDocumentType || null,
      });
      setDetail({ profile: updated, own });
      notifySuccess("MSG-90");
    });
  }

  return (
    <div className="space-y-6">
      <Link className="text-sm underline" href={PATIENT_ROUTES.profiles}>
        Về danh sách hồ sơ
      </Link>
      <PortalPageHeader
        title={profile.fullName}
        description="Thông tin hành chính"
      />
      <form
        onSubmit={save}
        className="max-w-xl space-y-4 rounded-lg border bg-card p-6"
      >
        <fieldset
          disabled={pending || confirming}
          className="grid gap-4 sm:grid-cols-2"
        >
          <label className="grid gap-1.5 sm:col-span-2">
            Họ và tên
            <Input name="fullName" required defaultValue={profile.fullName} />
          </label>
          <label className="grid gap-1.5">
            Ngày sinh
            <Input
              name="dateOfBirth"
              type="date"
              required
              defaultValue={profile.dateOfBirth}
            />
          </label>
          <label className="grid gap-1.5">
            Giới tính
            <select
              name="gender"
              defaultValue={profile.gender}
              className="h-9 rounded-md border bg-background px-3"
            >
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
              required={own}
              defaultValue={profile.phoneCanonical ?? ""}
            />
          </label>
          <label className="grid gap-1.5">
            Địa chỉ
            <Input name="address" defaultValue={profile.address ?? ""} />
          </label>
          <label className="grid gap-1.5">
            Loại giấy tờ
            <select
              name="identityDocumentType"
              defaultValue={profile.identityDocumentType ?? ""}
              className="h-9 rounded-md border bg-background px-3"
            >
              <option value="">Chưa cung cấp</option>
              <option value="CCCD">Căn cước công dân</option>
              <option value="PERSONAL_ID">Số định danh cá nhân</option>
              <option value="PASSPORT">Hộ chiếu</option>
            </select>
          </label>
          <label className="grid gap-1.5">
            Số giấy tờ
            <Input name="nationalId" defaultValue={profile.nationalId ?? ""} />
          </label>
        </fieldset>
        <Button type="submit" disabled={pending || confirming}>
          Lưu thông tin
        </Button>
      </form>
      {!own &&
        (confirming ? (
          <section
            aria-label="Xác nhận gỡ liên kết"
            className="max-w-xl space-y-3 rounded-lg border p-4"
          >
            <p>
              Liên kết chăm sóc với {profile.fullName} sẽ kết thúc. Hồ sơ và
              lịch sử khám tại phòng khám vẫn được giữ.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                disabled={pending}
                onClick={() => setConfirming(false)}
              >
                Giữ liên kết
              </Button>
              <Button
                variant="destructive"
                disabled={pending}
                onClick={() =>
                  void run(async () => {
                    await removeDependent(profileId);
                    notifySuccess("MSG-91");
                    router.replace(PATIENT_ROUTES.profiles);
                  })
                }
              >
                Xác nhận gỡ
              </Button>
            </div>
          </section>
        ) : (
          <Button
            variant="outline"
            disabled={pending}
            onClick={() => setConfirming(true)}
          >
            Gỡ liên kết
          </Button>
        ))}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
