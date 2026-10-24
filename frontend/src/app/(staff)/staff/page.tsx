import { redirect } from "next/navigation";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

// Trang mặc định của khu Lễ tân là tiếp nhận bệnh nhân.
export default function StaffHomePage() {
  redirect(STAFF_ROUTES.checkIn);
}
