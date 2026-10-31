import { redirect } from "next/navigation";

// Trang chủ khu bệnh nhân. Các khối nội dung thuộc feature `_home`.
export default function PatientPortalHomePage() {
  redirect("/portal/account");
}
