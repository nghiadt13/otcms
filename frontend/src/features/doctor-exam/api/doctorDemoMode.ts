// Công tắc dữ liệu mẫu của khu bác sĩ. Dữ liệu mẫu chỉ để xem/chạy thử giao diện khi BE khám bệnh
// chưa đủ; không bao giờ tự bật ở production trừ khi build có NEXT_PUBLIC_DOCTOR_DEMO=true
// (giống NEXT_PUBLIC_RECEPTION_DEMO của khu lễ tân).

const STORAGE_KEY = "otcms.doctorDemo";
const CHANGE_EVENT = "otcms:doctor-demo-change";

const forcedByBuild = () => process.env.NEXT_PUBLIC_DOCTOR_DEMO === "true";
const isProduction = () => process.env.NODE_ENV === "production";

export function isDoctorDemoMode(): boolean {
  if (forcedByBuild()) return true;
  if (isProduction() || typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

/** Người dùng có được tự bật/tắt dữ liệu mẫu không (không phải production, không bị build ép). */
export function canToggleDoctorDemo(): boolean {
  return !forcedByBuild() && !isProduction();
}

export function setDoctorDemoMode(on: boolean): void {
  if (!canToggleDoctorDemo()) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, on ? "1" : "0");
  } catch {
    return;
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Dùng với useSyncExternalStore. */
export function subscribeDoctorDemoMode(onChange: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}
