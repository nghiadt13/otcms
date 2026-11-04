"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";

import { notifySuccess } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  addDependent,
  dependentErrorMessage,
  resendDependentOtp,
  verifyDependentOtp,
  type DependentLinkResult,
  type DependentRelationship,
} from "../api/dependentApi";

const relationships: DependentRelationship[] = [
  "PARENT",
  "CHILD",
  "SPOUSE",
  "SIBLING",
];
const selectClass = "h-9 w-full rounded-md border bg-background px-3 text-sm";

export function NewPatientProfileScreen() {
  const router = useRouter();
  const inFlight = useRef(false);
  const [pending, setPending] = useState(false);
  const [link, setLink] = useState<DependentLinkResult | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [resent, setResent] = useState(false);

  async function run(
    action: () => Promise<DependentLinkResult>,
    resend = false,
  ) {
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    setError("");
    setResent(false);
    try {
      const result = await action();
      if (result.result === "LINKED") {
        notifySuccess("MSG-90");
        router.push(PATIENT_ROUTES.profiles);
      }
      setLink(result);
      if (resend) {
        setCode("");
        setResent(result.result === "OTP_SENT");
      }
    } catch (cause) {
      setError(dependentErrorMessage(cause));
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? "").trim();
    const nationalId = value("nationalId");
    const identityDocumentType = value("identityDocumentType");
    if (Boolean(nationalId) !== Boolean(identityDocumentType)) {
      setError("Chọn loại giấy tờ và nhập số giấy tờ tương ứng.");
      return;
    }
    void run(() =>
      addDependent({
        fullName: value("fullName"),
        dateOfBirth: value("dateOfBirth"),
        gender: value("gender"),
        relationship: value("relationship") as DependentRelationship,
        ...(nationalId ? { nationalId, identityDocumentType } : {}),
      }),
    );
  }

  return (
    <>
      <PortalPageHeader
        title="Thêm người thân"
        description="Nhập thông tin của người thân để gửi yêu cầu liên kết hồ sơ."
      />
      <section className="max-w-xl space-y-4 rounded-lg border bg-card p-6">
        {link === null && (
          <form onSubmit={submit} className="space-y-4">
            <fieldset disabled={pending} className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 sm:col-span-2">
                Họ và tên
                <Input name="fullName" required autoComplete="off" />
              </label>
              <label className="grid gap-1.5">
                Ngày sinh
                <Input name="dateOfBirth" type="date" required />
              </label>
              <label className="grid gap-1.5">
                Giới tính
                <select
                  name="gender"
                  className={selectClass}
                  required
                  defaultValue=""
                >
                  <option value="" disabled>
                    Chọn giới tính
                  </option>
                  <option value="MALE">Nam</option>
                  <option value="FEMALE">Nữ</option>
                  <option value="OTHER">Khác</option>
                </select>
              </label>
              <label className="grid gap-1.5 sm:col-span-2">
                Quan hệ với bạn
                <select
                  name="relationship"
                  className={selectClass}
                  required
                  defaultValue=""
                >
                  <option value="" disabled>
                    Chọn quan hệ
                  </option>
                  {relationships.map((relationship) => (
                    <option key={relationship} value={relationship}>
                      {CARE_RELATIONSHIP_LABELS[relationship]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="grid gap-1.5">
                Loại giấy tờ
                <select name="identityDocumentType" className={selectClass}>
                  <option value="">Chưa cung cấp</option>
                  <option value="CCCD">Căn cước công dân</option>
                  <option value="PERSONAL_ID">Số định danh cá nhân</option>
                  <option value="PASSPORT">Hộ chiếu</option>
                </select>
              </label>
              <label className="grid gap-1.5">
                Số giấy tờ
                <Input name="nationalId" autoComplete="off" />
              </label>
            </fieldset>
            <Button type="submit" disabled={pending}>
              {pending ? "Đang gửi…" : "Gửi yêu cầu"}
            </Button>
          </form>
        )}
        {link?.result === "OTP_SENT" && (
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void run(() => verifyDependentOtp(link.linkId, code));
            }}
          >
            <h2 className="font-semibold">Xác minh mã OTP</h2>
            <p>Mã xác minh đã được gửi đến {link.maskedEmail}.</p>
            <label className="grid gap-1.5">
              Mã xác minh
              <Input
                value={code}
                onChange={(event) => setCode(event.target.value)}
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                required
                disabled={pending}
              />
            </label>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={pending}>
                Xác nhận mã
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={pending}
                onClick={() =>
                  void run(() => resendDependentOtp(link.linkId), true)
                }
              >
                Gửi lại mã
              </Button>
            </div>
            {resent && <p role="status">Đã gửi lại mã xác minh.</p>}
          </form>
        )}
        {link?.result === "COUNTER_REQUIRED" && (
          <div role="status" className="space-y-2">
            <h2 className="font-semibold">Chờ xác nhận tại quầy</h2>
            <p>
              Mang giấy tờ của người thân đến quầy lễ tân để xác minh. Hồ sơ sẽ
              xuất hiện trong bộ chọn sau khi lễ tân xác nhận.
            </p>
          </div>
        )}
        {link?.result === "LINKED" && <p role="status">Đã liên kết hồ sơ.</p>}
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
        <Link className="text-sm underline" href={PATIENT_ROUTES.profiles}>
          Về danh sách hồ sơ
        </Link>
      </section>
    </>
  );
}
