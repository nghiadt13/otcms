import type { StaffProfileInput } from "../types/staffProfile";

/** multipart: part `data` là JSON, part `photo` là file tùy chọn. */
export function buildStaffProfileFormData(
  input: StaffProfileInput,
  photo?: File,
): FormData {
  const form = new FormData();
  form.append(
    "data",
    new Blob([JSON.stringify(input)], { type: "application/json" }),
  );
  if (photo) form.append("photo", photo);
  return form;
}
