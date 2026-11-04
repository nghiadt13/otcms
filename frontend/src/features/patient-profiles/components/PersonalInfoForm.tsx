"use client";

import { useId, useState, type FormEvent, type ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  getOwnPatientProfile,
  updateOwnPatientProfile,
} from "../api/ownPatientProfileApi";
import {
  asOwnProfileStaleError,
  isOwnProfileAccessLost,
  isOwnProfileStale,
  ownProfileDisplayError,
  ownProfileErrorMessage,
  ownProfileFieldErrors,
} from "../api/ownPatientProfileErrors";
import { formatDateOfBirthText } from "../schema/dateOfBirthText";
import {
  validateProfileFields,
  type ProfileFieldErrors,
} from "../schema/patientProfileFieldRules";
import {
  ownProfileUpdateFromValues,
  ownProfileValidationValues,
  ownProfileValuesFromDetail,
  type OwnPatientProfileFormValues,
} from "../schema/ownPatientProfileMapping";
import type { OwnPatientProfile } from "../types/ownPatientProfile";
import { ProfileTextField } from "./ProfileTextField";

const EDITED_FIELDS = [
  "fullName",
  "dateOfBirth",
  "sex",
  "citizenId",
  "phone",
  "address",
] as const;

export function PersonalInfoForm({
  detail,
  emailSlot,
  onSaved,
}: {
  detail: OwnPatientProfile;
  emailSlot: ReactNode;
  onSaved: (detail: OwnPatientProfile) => void;
}) {
  const id = useId();
  const [serverProfile, setServerProfile] = useState(detail);
  const [baseline, setBaseline] = useState(() =>
    ownProfileValuesFromDetail(detail),
  );
  const [values, setValues] = useState(() =>
    ownProfileValuesFromDetail(detail),
  );
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [pending, setPending] = useState(false);
  const [stale, setStale] = useState(false);
  const [reloadPending, setReloadPending] = useState(false);
  const [unavailable, setUnavailable] = useState<string | null>(null);

  const changed = EDITED_FIELDS.filter(
    (field) => values[field] !== baseline[field],
  );
  const identityTypeChanged =
    values.identityDocumentType !== baseline.identityDocumentType;
  const dirty = changed.length > 0 || identityTypeChanged;

  function update<K extends keyof OwnPatientProfileFormValues>(
    field: K,
    value: OwnPatientProfileFormValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  function discard() {
    setValues(baseline);
    setErrors({});
  }

  async function reloadLatest() {
    const replacingUnavailableSession = unavailable !== null;
    setReloadPending(true);
    try {
      const latest = await getOwnPatientProfile();
      setServerProfile(latest);
      const latestValues = ownProfileValuesFromDetail(latest);
      setBaseline(latestValues);
      if (replacingUnavailableSession) setValues(latestValues);
      setErrors({});
      setStale(false);
      setUnavailable(null);
      // Keep the user's draft. Only the baseline and write version move to server data.
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") return;
      if (
        isOwnProfileAccessLost(error) ||
        (typeof error === "object" &&
          error !== null &&
          "status" in error &&
          [401, 403, 404].includes(Number(error.status)))
      ) {
        setUnavailable(ownProfileErrorMessage(error));
        const emptyValues: OwnPatientProfileFormValues = {
          fullName: "",
          dateOfBirth: "",
          sex: "",
          phone: "",
          address: "",
          citizenId: "",
          identityDocumentType: "",
        };
        setValues(emptyValues);
        setBaseline(emptyValues);
      }
      notifyError(ownProfileDisplayError(error));
    } finally {
      setReloadPending(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!dirty || stale || pending || unavailable) return;
    const local = validateProfileFields(
      ownProfileValidationValues(values),
      EDITED_FIELDS,
    );
    if (values.citizenId.trim() && !values.identityDocumentType)
      local.citizenId = "Chọn loại giấy tờ định danh.";
    setErrors(local);
    const firstInvalid = EDITED_FIELDS.find((field) => local[field]);
    if (firstInvalid) {
      document.getElementById(`${id}-${firstInvalid}`)?.focus();
      return;
    }

    setPending(true);
    try {
      const updated = await updateOwnPatientProfile(
        ownProfileUpdateFromValues(values, serverProfile),
      );
      const next = ownProfileValuesFromDetail(updated);
      setServerProfile(updated);
      setBaseline(next);
      setValues(next);
      setErrors({});
      setStale(false);
      notifySuccess("MSG-05");
      onSaved(updated);
    } catch (error) {
      if (isOwnProfileStale(error)) {
        setStale(true);
        notifyError(asOwnProfileStaleError());
      } else if (ownProfileFieldErrors(error)) {
        setErrors(ownProfileFieldErrors(error) ?? {});
      } else {
        if (
          isOwnProfileAccessLost(error) ||
          (typeof error === "object" &&
            error !== null &&
            "status" in error &&
            [401, 403, 404].includes(Number(error.status)))
        ) {
          setUnavailable(ownProfileErrorMessage(error));
          const emptyValues: OwnPatientProfileFormValues = {
            fullName: "",
            dateOfBirth: "",
            sex: "",
            phone: "",
            address: "",
            citizenId: "",
            identityDocumentType: "",
          };
          setValues(emptyValues);
          setBaseline(emptyValues);
        }
        notifyError(ownProfileDisplayError(error));
      }
    } finally {
      setPending(false);
    }
  }

  if (unavailable) {
    return (
      <div className="grid gap-3" role="alert">
        <p>{unavailable}</p>
        <button
          className={publicButtonClass("secondary")}
          disabled={reloadPending}
          onClick={() => void reloadLatest()}
          type="button"
        >
          {reloadPending ? "Đang tải..." : "Tải lại hồ sơ"}
        </button>
      </div>
    );
  }

  return (
    <form
      className="grid gap-8"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <section aria-labelledby={`${id}-identity`} className="grid gap-5">
        <h3 className="text-lg font-semibold" id={`${id}-identity`}>
          Thông tin cá nhân
        </h3>
        <div className="grid items-start gap-5 sm:grid-cols-2">
          <ProfileTextField
            autoComplete="name"
            disabled={pending}
            error={errors.fullName}
            id={`${id}-fullName`}
            label="Họ và tên"
            onChange={(next) => update("fullName", next)}
            required
            value={values.fullName}
          />
          <ProfileTextField
            disabled={pending}
            error={errors.dateOfBirth}
            hint="Gõ liền, ví dụ 20051990."
            id={`${id}-dateOfBirth`}
            inputMode="numeric"
            label="Ngày sinh"
            onChange={(next) =>
              update("dateOfBirth", formatDateOfBirthText(next))
            }
            placeholder="dd/mm/yyyy"
            required
            value={values.dateOfBirth}
          />
          <fieldset className="grid gap-2">
            <legend className="text-base font-medium">
              Giới tính{" "}
              <span aria-hidden className="text-destructive">
                {" "}
                *
              </span>
            </legend>
            <RadioGroup
              aria-describedby={errors.sex ? `${id}-sex-error` : undefined}
              className="grid-cols-3 gap-3"
              disabled={pending}
              onValueChange={(next) => {
                if (next === "MALE" || next === "FEMALE" || next === "OTHER")
                  update("sex", next);
              }}
              value={values.sex}
            >
              {(
                [
                  ["MALE", "Nam"],
                  ["FEMALE", "Nữ"],
                  ["OTHER", "Khác"],
                ] as const
              ).map(([value, label]) => (
                <Label
                  className="flex min-h-12 items-center gap-3 rounded-lg border px-3 text-base font-normal"
                  key={value}
                >
                  <RadioGroupItem value={value} />
                  {label}
                </Label>
              ))}
            </RadioGroup>
            {errors.sex ? (
              <p className="text-sm text-destructive" role="alert">
                {errors.sex}
              </p>
            ) : null}
          </fieldset>
          <div className="grid gap-2">
            <ProfileTextField
              disabled={pending}
              error={errors.citizenId}
              hint="Không bắt buộc. Trẻ em dùng mã định danh hoặc hộ chiếu."
              id={`${id}-citizenId`}
              label="CCCD / Mã định danh"
              onChange={(next) => update("citizenId", next)}
              value={values.citizenId}
            />
            {values.citizenId.trim() ? (
              <label
                className="grid gap-2 text-base font-medium"
                htmlFor={`${id}-identityDocumentType`}
              >
                Loại giấy tờ định danh
                <select
                  className="h-12 rounded-md border bg-background px-3"
                  disabled={pending}
                  id={`${id}-identityDocumentType`}
                  onChange={(event) =>
                    update(
                      "identityDocumentType",
                      event.target
                        .value as OwnPatientProfileFormValues["identityDocumentType"],
                    )
                  }
                  required
                  value={values.identityDocumentType}
                >
                  <option value="">Chọn loại giấy tờ</option>
                  <option value="CCCD">CCCD</option>
                  <option value="PERSONAL_ID">Mã định danh cá nhân</option>
                  <option value="PASSPORT">Hộ chiếu</option>
                </select>
              </label>
            ) : null}
          </div>
        </div>
      </section>

      <section aria-labelledby={`${id}-contact`} className="grid gap-5">
        <h3 className="text-lg font-semibold" id={`${id}-contact`}>
          Liên hệ
        </h3>
        <div className="grid items-start gap-5 sm:grid-cols-2">
          <ProfileTextField
            autoComplete="tel"
            disabled={pending}
            error={errors.phone}
            hint="Dùng để phòng khám gọi xác nhận lịch. Không cần xác minh."
            id={`${id}-phone`}
            inputMode="tel"
            label="Số điện thoại"
            onChange={(next) => update("phone", next)}
            required
            value={values.phone}
          />
          {emailSlot}
          <div className="sm:col-span-2">
            <ProfileTextField
              autoComplete="street-address"
              disabled={pending}
              error={errors.address}
              id={`${id}-address`}
              label="Địa chỉ"
              onChange={(next) => update("address", next)}
              required
              value={values.address}
            />
          </div>
        </div>
      </section>

      {stale ? (
        <div
          className="grid gap-3 rounded-lg border border-destructive/40 p-4"
          role="alert"
        >
          <p>{formatAppMessage("MSG-16")}</p>
          <button
            className={publicButtonClass("secondary")}
            disabled={reloadPending}
            onClick={() => void reloadLatest()}
            type="button"
          >
            {reloadPending ? "Đang tải..." : "Tải lại hồ sơ mới nhất"}
          </button>
        </div>
      ) : null}

      <div className="flex flex-col gap-4 border-t pt-6 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground" role="status">
          {dirty ? "Có thay đổi chưa lưu." : "Chưa có thay đổi."}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <button
            className={publicButtonClass("secondary")}
            disabled={!dirty || pending}
            onClick={discard}
            type="button"
          >
            Hủy thay đổi
          </button>
          <button
            className={publicButtonClass("primary")}
            disabled={!dirty || pending || stale}
            type="submit"
          >
            {pending ? "Đang lưu..." : "Lưu thay đổi"}
          </button>
        </div>
      </div>
    </form>
  );
}
