import { formatDate } from "@/lib/format/formatDate";
import type { PatientProfileDetail } from "../types/patientProfile";
import { SEX_LABELS } from "./profileOptions";

// Thông tin hành chính của hồ sơ, chỉ đọc. Ô trống ghi "Chưa có" để người dùng biết không phải lỗi.
export function ProfileInfoList({ detail }: { detail: PatientProfileDetail }) {
  const items = [
    { label: "Ngày sinh", value: formatDate(detail.dateOfBirth) },
    { label: "Giới tính", value: SEX_LABELS[detail.sex] },
    { label: "Số điện thoại", value: detail.phone },
    { label: "Địa chỉ", value: detail.address },
    { label: "CCCD/Mã định danh", value: detail.citizenId },
    { label: "Email", value: detail.email },
  ];

  return (
    <div className="space-y-3">
      <dl className="grid gap-x-6 gap-y-4 rounded-lg border bg-card px-4 py-4 sm:grid-cols-2">
        {items.map(({ label, value }) => (
          <div className="min-w-0" key={label}>
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd className="mt-1 text-base break-words">{value || "Chưa có"}</dd>
          </div>
        ))}
      </dl>
      {detail.hasVisits ? (
        <p className="text-sm text-muted-foreground">
          Họ tên và ngày sinh không sửa trên web vì hồ sơ đã có lượt khám. Muốn
          đổi, nhờ lễ tân sửa tại quầy.
        </p>
      ) : null}
    </div>
  );
}
