"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useId, useRef, useState } from "react";
import { usePatientLookupSearch } from "../hooks/usePatientLookupSearch";
import { toDateOfBirthText } from "../schema/patientFieldRules";
import { buildLookupQuery } from "../schema/patientRequestRules";
import type { PatientMatch, PatientSearchMode } from "../types/patient";
import type {
  DuplicateCriteria,
  WalkInInitialValues,
} from "../types/patientSearchState";
import { PatientLookupForm, type LookupFormValues } from "./PatientLookupForm";
import { PatientLookupResults } from "./PatientLookupResults";
import { WalkInProfileDialog } from "./WalkInProfileDialog";

export type PatientLookupProps = {
  /** Đường dẫn nhận `?patientId=` khi chọn hồ sơ hoặc vừa tạo hồ sơ (thay mọi tham số khác). */
  selectHref: string;
  /** Đưa focus vào ô đầu tiên khi mở màn. */
  autoFocus?: boolean;
};

const EMPTY_VALUES: LookupFormValues = {
  mode: "phone",
  phone: "",
  dateOfBirth: "",
  patientCode: "",
  fullName: "",
};

const EMPTY_INITIAL: WalkInInitialValues = {
  phone: "",
  dateOfBirth: "",
  fullName: "",
};

/**
 * Tìm hồ sơ bệnh nhân tại quầy (BR-14: tìm trước, tạo sau). Chọn hoặc tạo xong thì đưa
 * `?patientId=` lên `selectHref`; màn đích quyết định phần việc tiếp theo.
 */
export function PatientLookup({ selectHref, autoFocus = false }: PatientLookupProps) {
  const router = useRouter();
  const titleId = useId();
  const { search, askFullName, errors, setErrors, run, reset } =
    usePatientLookupSearch();
  const [values, setValues] = useState<LookupFormValues>(EMPTY_VALUES);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogInitial, setDialogInitial] =
    useState<WalkInInitialValues>(EMPTY_INITIAL);
  const fullNameRef = useRef<HTMLInputElement>(null);

  // MULTIPLE: đưa con trỏ vào ô họ tên để lễ tân gõ tiếp ngay.
  useEffect(() => {
    if (askFullName) fullNameRef.current?.focus();
  }, [askFullName]);

  function patch(next: Partial<LookupFormValues>) {
    setValues((current) => ({ ...current, ...next }));
  }

  function changeMode(mode: PatientSearchMode) {
    reset();
    patch({ mode });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (search.kind === "loading") return;
    const built = buildLookupQuery({ ...values, askFullName });
    if ("errors" in built) {
      setErrors(built.errors);
      return;
    }
    const { query } = built;
    const prefill =
      "phone" in query
        ? {
            phone: query.phone,
            dateOfBirth: toDateOfBirthText(query.dateOfBirth),
            fullName: query.fullName ?? "",
          }
        : null;
    void run(query, prefill);
  }

  function openPatient(patientId: string) {
    router.push(`${selectHref}?patientId=${encodeURIComponent(patientId)}`);
  }

  function openWalkInDialog() {
    if (search.kind !== "ready" || !search.prefill) return;
    setDialogInitial(search.prefill);
    setDialogOpen(true);
  }

  // Tạo xong: bỏ kết quả "không thấy" cũ, màn đích hiện hồ sơ mới.
  function handleCreated(patient: PatientMatch) {
    setDialogOpen(false);
    reset();
    openPatient(patient.id);
  }

  // 409 MSG-18: đã có hồ sơ khớp. Điền lại đúng tiêu chí vừa gửi rồi tìm lại.
  function handleDuplicate(criteria: DuplicateCriteria) {
    setDialogOpen(false);
    const dateOfBirth = toDateOfBirthText(criteria.dateOfBirth);
    patch({ mode: "phone", phone: criteria.phone, dateOfBirth, fullName: "" });
    reset();
    void run(
      { phone: criteria.phone, dateOfBirth: criteria.dateOfBirth },
      { phone: criteria.phone, dateOfBirth, fullName: "" },
    );
  }

  return (
    <section
      aria-labelledby={titleId}
      className="grid gap-4 rounded-lg border bg-card p-4"
    >
      <div className="grid gap-1">
        <h2 id={titleId} className="text-lg font-semibold">
          Tìm bệnh nhân
        </h2>
        <p className="text-sm text-muted-foreground">
          Tìm bằng số điện thoại và ngày sinh, hoặc bằng mã bệnh nhân.
        </p>
      </div>
      <PatientLookupForm
        values={values}
        errors={errors}
        askFullName={askFullName}
        searching={search.kind === "loading"}
        autoFocus={autoFocus}
        fullNameRef={fullNameRef}
        onModeChange={changeMode}
        onChange={patch}
        onSubmit={handleSubmit}
      />
      <PatientLookupResults
        mode={values.mode}
        state={search}
        onSelect={(match) => openPatient(match.id)}
        onCreate={openWalkInDialog}
      />
      <WalkInProfileDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        initial={dialogInitial}
        onCreated={handleCreated}
        onDuplicate={handleDuplicate}
      />
    </section>
  );
}
