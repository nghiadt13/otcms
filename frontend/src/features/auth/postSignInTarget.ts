import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

const SITE_HOME = "/";

// Đích thật sau khi bệnh nhân đăng nhập (UC01 AF): tài khoản chưa có hồ sơ "Bản thân" (vừa
// đăng ký xong) được đưa tới màn Thông tin cá nhân để điền, giữ lại đích ban đầu làm `next`
// để điền xong quay về đúng chỗ. Không đọc được danh sách hồ sơ thì cứ về đích cũ: cổng khu
// bệnh nhân sẽ chặn lại nếu hồ sơ thật sự thiếu, nên lỗi mạng ở đây không làm kẹt đăng nhập.
export async function postSignInTarget(target: string): Promise<string> {
  try {
    const { items } = await listLinkedProfiles();
    if (items.some((profile) => profile.relationship === "SELF")) return target;
  } catch {
    return target;
  }
  return target === SITE_HOME
    ? PATIENT_ROUTES.account
    : `${PATIENT_ROUTES.account}?next=${encodeURIComponent(target)}`;
}
