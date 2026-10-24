import { getCheckInContext } from "../api/checkInApi";
import type { CheckInContextQuery, CheckInTarget } from "../types/checkIn";
import { useReceptionLoader } from "./useReceptionLoader";

/** Bối cảnh tiếp nhận của đích đang chọn; `reload()` sau khi lễ tân khác thay đổi trạng thái. */
export function useCheckInContext(target: CheckInTarget) {
  const query: CheckInContextQuery =
    target.kind === "appointment" ? { appointmentId: target.id } : { patientId: target.id };
  return useReceptionLoader(`context:${target.kind}:${target.id}`, (signal) =>
    getCheckInContext(query, signal),
  );
}
