import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { BookableService, BookedFor } from "../types/booking";
import { DOCTOR_IDS } from "./appointmentMockData";

// DANH MỤC GIẢ của luồng đặt lịch: dịch vụ Khám, bác sĩ và ca, người có thể đặt. Chỉ lớp API
// giả và dữ liệu seed đọc file này. Mã bác sĩ dùng lại mã của mock admin để màn Lễ tân lọc theo
// bác sĩ khớp với danh sách admin.

/** Bước giờ khám (phút). */
export const SLOT_MINUTES = 15;
/** Ca làm việc trong ngày, phút từ 00:00: sáng 08:00–11:30, chiều 13:30–17:00. */
export const SHIFT_WINDOWS_MINUTES: readonly (readonly [number, number])[] = [
  [8 * 60, 11 * 60 + 30],
  [13 * 60 + 30, 17 * 60],
];
/** Dải đặt lịch: hôm nay + 13 ngày (14 ô trên dải). */
export const HORIZON_DAYS = 14;

/** Chỉ dịch vụ loại Khám được đặt (chốt 23/10). Đã sắp theo tên tiếng Việt. */
export const BOOKABLE_SERVICES: readonly BookableService[] = [
  {
    id: "svc-kham-tq",
    name: "Khám chỉnh hình tổng quát",
    price: 200000,
    bookingDetails: "Mang theo phim chụp cũ nếu có.",
  },
  {
    id: "svc-kham-cs",
    name: "Khám cột sống",
    price: 250000,
    bookingDetails: null,
  },
];

export interface BookingDoctor {
  id: string;
  fullName: string;
  /** Tên phòng khám, ghi vào lịch khi đặt. */
  room: string;
  /** Ngày trong tuần có ca: 0 = Chủ nhật. */
  weekdays: readonly number[];
}

export const BOOKING_DOCTORS: readonly BookingDoctor[] = [
  {
    id: DOCTOR_IDS.ha,
    fullName: "BS. Phạm Thu Hà",
    room: "Phòng khám 2",
    weekdays: [1, 2, 3, 4, 5, 6],
  },
  {
    id: DOCTOR_IDS.minh,
    fullName: "BS. Lê Quang Minh",
    room: "Phòng khám 1",
    weekdays: [1, 2, 3, 4, 5],
  },
  {
    id: DOCTOR_IDS.tuan,
    fullName: "BS. Đỗ Minh Tuấn",
    room: "Phòng khám 3",
    weekdays: [2, 3, 4, 5, 6],
  },
];

export const doctorById = (id: string): BookingDoctor | undefined =>
  BOOKING_DOCTORS.find((doctor) => doctor.id === id);

// Kịch bản của kho giả (mô tả trong phase-03 mục 8). Đừng đổi giá trị: test dựa vào chúng.
/** Bác sĩ này không nhận đặt mới vào ngày `REFUSING_DAY_OFFSET` → MSG-24 khi chọn. */
export const REFUSING_DOCTOR_ID = DOCTOR_IDS.tuan;
export const REFUSING_DAY_OFFSET = 1;
/** Giờ này của hôm nay đã có người khác đặt trước → MSG-30 khi đặt, và biến khỏi danh sách. */
export const TAKEN_TODAY_TIME = "09:00";
/** Ngày thứ ba trên dải đã kín lịch mọi bác sĩ → không có giờ. */
export const FULL_DAY_OFFSET = 2;
/** Dịch vụ giả lập lỗi máy chủ → MSG-04 (chỉ dùng trong test). */
export const FAILING_SERVICE_ID = "svc-error";
/** Bệnh nhân bị khóa đặt lịch → MSG-29. */
export const LOCKED_PATIENT_ID = "pat-locked";
/**
 * Bác sĩ vắng cả ngày hôm nay: không nhận đặt và không là ứng viên chuyển lịch trong ngày. Lịch
 * của bác sĩ này hôm nay (seed apt-1041) không có ai thay ở giờ đó → AF1, Lễ tân chỉ còn "Báo cho
 * bệnh nhân". Đổi giá trị thì test Lễ tân đổi theo.
 */
export const ABSENT_TODAY_DOCTOR_ID = DOCTOR_IDS.minh;

interface DirectoryPatient {
  id: string;
  patientCode: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  phone: string | null;
  locked?: boolean;
}

/**
 * Người do lễ tân quản lý (mã `pat-*`, tên và SĐT khớp kho giả của khu Lễ tân; feature này không
 * import feature đó). Mã "pat-an" và mã hồ sơ gắn tài khoản của cùng người (cùng BN-000123) được
 * quy về một mã: xem `resolveBookingPatient`.
 */
const RECEPTION_DIRECTORY: readonly DirectoryPatient[] = [
  { id: "pat-an", patientCode: "BN-000123", fullName: "Nguyễn Văn An", dateOfBirth: "1992-07-21", phone: "0912345678" },
  { id: "pat-binh", patientCode: "BN-000124", fullName: "Trần Thị Bình", dateOfBirth: "1985-09-02", phone: "0987123456" },
  { id: "pat-cuong", patientCode: "BN-000125", fullName: "Lê Văn Cường", dateOfBirth: "1971-11-20", phone: "0977555123" },
  { id: "pat-em", patientCode: "BN-000127", fullName: "Bùi Thị Em", dateOfBirth: "2001-12-30", phone: "0966112233" },
  { id: "pat-lan", patientCode: "BN-000456", fullName: "Nguyễn Thị Lan", dateOfBirth: "1936-03-14", phone: "0934567891" },
  { id: LOCKED_PATIENT_ID, patientCode: "BN-000199", fullName: "Hồ Thị Khóa", dateOfBirth: "1990-01-01", phone: null, locked: true },
];

/** Người được đặt, đã quy về mã canonical (mã hồ sơ gắn tài khoản nếu người đó đã gắn). */
export interface ResolvedBookingPatient {
  id: string;
  fullName: string;
  patientCode: string;
  dateOfBirth: string;
  phone: string | null;
  bookedFor: BookedFor;
  locked: boolean;
}

function fromLinked(
  profile: LinkedProfile,
  holder: LinkedProfile | undefined,
): ResolvedBookingPatient {
  const phone =
    RECEPTION_DIRECTORY.find((entry) => entry.patientCode === profile.patientCode)
      ?.phone ?? null;
  const bookedFor: BookedFor =
    profile.relationship === "SELF" || !holder
      ? { kind: "SELF" }
      : { kind: "DEPENDENT", accountHolderName: holder.fullName };
  return {
    id: profile.id,
    fullName: profile.fullName,
    patientCode: profile.patientCode,
    dateOfBirth: profile.dateOfBirth,
    phone,
    bookedFor,
    locked: false,
  };
}

/**
 * Tìm người đặt theo mã trong yêu cầu. Hồ sơ đang gắn tài khoản (`linked`) thắng: BR-21, bỏ gắn
 * thì mất quyền đặt. Mã lễ tân trùng mã BN với hồ sơ gắn thì quy về mã hồ sơ gắn. Không có → null.
 */
export function resolveBookingPatient(
  patientId: string,
  linked: readonly LinkedProfile[],
): ResolvedBookingPatient | null {
  const holder = linked.find((profile) => profile.relationship === "SELF");
  const own = linked.find((profile) => profile.id === patientId);
  if (own) return fromLinked(own, holder);

  const entry = RECEPTION_DIRECTORY.find((item) => item.id === patientId);
  if (!entry) return null;
  const sameLinked = linked.find((profile) => profile.patientCode === entry.patientCode);
  if (sameLinked) return fromLinked(sameLinked, holder);

  return {
    id: entry.id,
    fullName: entry.fullName,
    patientCode: entry.patientCode,
    dateOfBirth: entry.dateOfBirth,
    phone: entry.phone,
    bookedFor: { kind: "SELF" },
    locked: entry.locked ?? false,
  };
}

/** Mã người đặt theo mã BN (dùng cho seed). Ưu tiên hồ sơ gắn tài khoản. */
export function patientIdOfCode(
  patientCode: string,
  linked: readonly LinkedProfile[],
): string | undefined {
  return (
    linked.find((profile) => profile.patientCode === patientCode)?.id ??
    RECEPTION_DIRECTORY.find((entry) => entry.patientCode === patientCode)?.id
  );
}
