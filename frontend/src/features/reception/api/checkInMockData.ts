import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { CheckInPatient, CheckInServiceOption, PriorityGroup } from "../types/checkIn";

// Dữ liệu giả của màn Tiếp nhận (chỉ lớp API giả dùng). "Hôm nay" lấy theo đồng hồ giờ Việt Nam
// lúc nạp module, nên danh sách luôn là lịch của ngày hiện tại. Mã bệnh nhân pat-an, pat-binh,
// pat-cuong, pat-dung, pat-em dùng chung với kịch bản của khu Bệnh nhân và Lễ tân (phase S04a).

export const MOCK_TODAY = todayIsoDate();
export const YESTERDAY = addDays(MOCK_TODAY, -1);
export const at = (date: string, time: string) => `${date}T${time}:00+07:00`;

export const ROOM_NAMES = ["Phòng khám 1", "Phòng khám 2"] as const;

export const ROOM_DOCTOR: Record<string, string> = {
  "Phòng khám 1": "BS. Lê Quang Minh",
  "Phòng khám 2": "BS. Phạm Thu Hà",
};

/** Số thứ tự đã cấp gần nhất của từng phòng hôm nay; số kế tiếp = số này + 1. */
export const QUEUE_COUNTER_SEED: Record<string, number> = {
  "Phòng khám 1": 8,
  "Phòng khám 2": 11,
};

export const SVC_GENERAL: CheckInServiceOption = {
  id: "svc-kham-tq",
  name: "Khám chỉnh hình tổng quát",
  price: 200000,
};
export const SVC_SPINE: CheckInServiceOption = {
  id: "svc-kham-cs",
  name: "Khám cột sống",
  price: 250000,
};

/** Dịch vụ Khám có thể chọn khi vãng lai (chỉ dịch vụ Khám được đặt lịch, BR chốt 23/10). */
export const WALK_IN_SERVICES: CheckInServiceOption[] = [SVC_GENERAL, SVC_SPINE];

/** Nhóm ưu tiên theo cấu hình (BR-31); thứ tự hiển thị là thứ tự của BE. */
export const PRIORITY_GROUPS: PriorityGroup[] = [
  { code: "EMERGENCY", label: "Cấp cứu" },
  { code: "CHILD_UNDER_6", label: "Trẻ em dưới 6 tuổi" },
  { code: "PREGNANT", label: "Phụ nữ có thai" },
  { code: "SEVERE_DISABILITY", label: "Người khuyết tật nặng" },
  { code: "AGE_75_PLUS", label: "Người từ 75 tuổi" },
  { code: "MERITORIOUS", label: "Người có công với cách mạng" },
];

const NAMED_PATIENTS: CheckInPatient[] = [
  { id: "pat-an", patientCode: "BN-000123", fullName: "Nguyễn Văn An", dateOfBirth: "1992-07-21", sex: "MALE", phone: "0912345678" },
  { id: "pat-binh", patientCode: "BN-000124", fullName: "Trần Thị Bình", dateOfBirth: "1990-07-05", sex: "FEMALE", phone: "0903111222" },
  { id: "pat-cuong", patientCode: "BN-000131", fullName: "Lê Văn Cường", dateOfBirth: "1972-11-30", sex: "MALE", phone: "0988777666" },
  { id: "pat-dung", patientCode: "BN-000140", fullName: "Phạm Thị Dung", dateOfBirth: "1985-04-18", sex: "FEMALE", phone: "0977555444" },
  { id: "pat-em", patientCode: "BN-000155", fullName: "Hoàng Văn Em", dateOfBirth: "1999-09-09", sex: "MALE", phone: "0966123123" },
  // Mẹ của An (BN-000456, cùng hồ sơ ở patients và lib/patient-scope). 90 tuổi: demo nhóm ưu tiên ≥ 75 tuổi.
  { id: "pat-lan", patientCode: "BN-000456", fullName: "Nguyễn Thị Lan", dateOfBirth: "1936-03-14", sex: "FEMALE", phone: "0934567891" },
  // Hồ sơ tạo ở UC10 (vãng lai): chỉ có lựa chọn Vãng lai.
  { id: "pat-moi", patientCode: "BN-000201", fullName: "Đỗ Thanh Hà", dateOfBirth: "1994-02-14", sex: "FEMALE", phone: "0944333222" },
];

const GENERIC_NAMES = [
  "Lương Thị Hoa", "Phan Văn Khải", "Bùi Thị Lan", "Đinh Quốc Minh", "Ngô Thị Nga",
  "Tạ Văn Phúc", "Hồ Thị Quyên", "Mai Đức Sơn", "Dương Thị Thảo", "Trịnh Văn Tài",
  "Lý Thị Uyên", "Cao Văn Vinh", "Đặng Thị Xuân", "Vương Minh Yến", "Hà Văn Hải",
  "Kiều Thị Hạnh", "Tôn Thất Long", "Phùng Thị Mai", "Châu Văn Nam", "Đào Thị Oanh",
  "Ung Văn Quang",
];

// Bệnh nhân còn lại trong danh sách hôm nay (đặt lịch thường).
const GENERIC_PATIENTS: CheckInPatient[] = GENERIC_NAMES.map((fullName, index) => {
  const n = index + 1;
  const pad = String(n).padStart(2, "0");
  return {
    id: `pat-p${pad}`,
    patientCode: `BN-0003${pad}`,
    fullName,
    dateOfBirth: `${1955 + n}-0${(n % 9) + 1}-1${n % 9}`,
    sex: n % 2 ? "FEMALE" : "MALE",
    phone: `0901${String(n).padStart(6, "0")}`,
  };
});

export const SEED_PATIENTS: CheckInPatient[] = [...NAMED_PATIENTS, ...GENERIC_PATIENTS];

/** Lượt khám Chờ kết luận còn trong 7 ngày: quay lại vào hàng đợi đọc kết quả, không thu phí. */
export interface OpenVisitSeed {
  visitId: string;
  firstCheckInDate: string;
  dayOfPeriod: number;
  nextQueue: { type: "RESULT_REVIEW"; roomName: string; doctorName: string };
}

export const SEED_OPEN_VISITS: Record<string, OpenVisitSeed> = {
  "pat-cuong": {
    visitId: "vis-6101",
    firstCheckInDate: addDays(MOCK_TODAY, -3),
    dayOfPeriod: 4,
    nextQueue: {
      type: "RESULT_REVIEW",
      roomName: "Phòng khám 2",
      doctorName: ROOM_DOCTOR["Phòng khám 2"],
    },
  },
};

/** Lượt trước đã Chưa hoàn tất và quá 7 ngày: lượt mới, thu phí mới (AF3). */
export const SEED_NOTICES: Record<string, "PREVIOUS_VISIT_INCOMPLETE"> = {
  "pat-dung": "PREVIOUS_VISIT_INCOMPLETE",
};
