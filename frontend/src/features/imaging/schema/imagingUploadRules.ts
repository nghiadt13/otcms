import type { ImagingOrder, UploadDraft } from "../types/imaging";
export const MAX_DICOM_BATCH_BYTES = 500 * 1024 * 1024;
export type ImagingUploadInput = Pick<
  UploadDraft,
  "patientId" | "orderId" | "uid" | "series" | "files" | "confirmed" | "reason"
>;
export function validateImagingUpload(
  input: ImagingUploadInput,
  order: Pick<ImagingOrder, "id" | "patientId">,
  replacement: boolean,
): string[] {
  const {
    patientId: patient,
    orderId: orderCode,
    uid,
    series,
    files,
    confirmed,
    reason,
  } = input;
  const id = order.id;
  const errors: string[] = [];
  if (!files.length)
    errors.push("Chưa có file DICOM. Chọn file hoặc sử dụng bộ dữ liệu mẫu.");
  if (files.some((f) => !f.valid))
    errors.push(
      "Có file không đúng định dạng DICOM được hỗ trợ. Chọn file .dcm có tiền tố DICM.",
    );
  if (patient.trim() !== order.patientId)
    errors.push(
      "Mã bệnh nhân của study không khớp với bệnh nhân trên chỉ định.",
    );
  if (orderCode.trim() !== id)
    errors.push("Mã chỉ định của study không khớp với chỉ định đang mở.");
  if (!/^\d+(\.\d+)+$/.test(uid) || uid.length > 64)
    errors.push(
      "Study Instance UID phải là chuỗi số phân tách bởi dấu chấm, tối đa 64 ký tự.",
    );
  if (!Number.isInteger(series) || series < 1)
    errors.push("Số series phải là số nguyên lớn hơn 0.");
  if (!confirmed)
    errors.push("Cần xác nhận đã đối chiếu đúng bệnh nhân và chỉ định.");
  if (replacement && !reason.trim())
    errors.push("Cần nhập lý do khi thay thế study đã gửi.");
  return errors;
}
