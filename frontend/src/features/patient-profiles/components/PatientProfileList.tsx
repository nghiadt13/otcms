import type { LinkedProfile } from "@/lib/patient-scope/types";
import { PatientProfileCard } from "./PatientProfileCard";

// Danh sách thành viên gia đình: mỗi thẻ một hàng, tự hiện đủ thông tin và nút Sửa/Xóa.
export function PatientProfileList({
  profiles,
  onChanged,
}: {
  profiles: LinkedProfile[];
  onChanged: () => void;
}) {
  return (
    <ul className="grid gap-4">
      {profiles.map((profile) => (
        <li key={profile.id}>
          <PatientProfileCard onChanged={onChanged} profile={profile} />
        </li>
      ))}
    </ul>
  );
}
