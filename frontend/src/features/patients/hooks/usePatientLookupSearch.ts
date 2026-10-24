import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { lookupPatients } from "../api/patientApi";
import {
  toPatientFieldErrors,
  type PatientFieldErrors,
} from "../schema/patientFieldRules";
import type { PatientLookupQuery } from "../types/patient";
import type {
  SearchState,
  WalkInInitialValues,
} from "../types/patientSearchState";

function isAbortError(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "name" in error &&
    error.name === "AbortError"
  );
}

/**
 * Trạng thái tìm của khối tìm bệnh nhân: kết quả, lỗi theo ô, và có cần ô họ tên không (BR-14).
 * Lần tìm mới hủy lần đang chạy; rời màn thì hủy hẳn để không cập nhật state sau khi unmount.
 */
export function usePatientLookupSearch() {
  const [search, setSearch] = useState<SearchState>({ kind: "idle" });
  const [askFullName, setAskFullName] = useState(false);
  const [errors, setErrors] = useState<PatientFieldErrors>({});
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  /** Tìm; MULTIPLE báo MSG-18 và bật ô họ tên; lỗi 400 hiện theo ô; lỗi khác là toast. */
  async function run(
    query: PatientLookupQuery,
    prefill: WalkInInitialValues | null,
  ): Promise<void> {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setErrors({});
    setSearch({ kind: "loading" });
    try {
      const response = await lookupPatients(query, controller.signal);
      setSearch({ kind: "ready", response, prefill });
      const multiple = response.result === "MULTIPLE";
      setAskFullName(multiple);
      if (multiple) toast.error(formatAppMessage("MSG-18"));
    } catch (error) {
      if (isAbortError(error)) return;
      setSearch({ kind: "idle" });
      if (isApiError(error) && error.fieldErrors?.length) {
        setErrors(toPatientFieldErrors(error.fieldErrors));
      } else {
        notifyError(error);
      }
    }
  }

  /** Về trạng thái chưa tìm (đổi cách tìm, hoặc bắt đầu lại sau khi trùng hồ sơ). */
  function reset(): void {
    abortRef.current?.abort();
    setSearch({ kind: "idle" });
    setAskFullName(false);
    setErrors({});
  }

  return { search, askFullName, errors, setErrors, run, reset };
}
