import type { StaffRole } from "@/lib/domain/clinicRoles";
import type { DoctorInfo, EligibleStaffAccount } from "../types/staffProfile";

// Kho dữ liệu giả trong bộ nhớ. Bản ghi lưu đủ trường; API dẫn ra dòng danh sách/chi tiết.
export type MockStaffRecord = {
  id: string;
  accountId: string;
  username: string;
  role: StaffRole;
  displayName: string;
  photoUrl: string | null;
  workContact: string;
  status: "ACTIVE" | "REMOVED";
  doctor: DoctorInfo | null;
};

/** Tài khoản có hồ sơ bị chặn gỡ vì còn lịch hẹn tương lai (ca kiểm thử MSG-36). */
export const USERNAME_WITH_FUTURE_APPOINTMENTS = "bs.trung";
/** Tiền tố tên đăng nhập luôn gặp lỗi dịch vụ khi tạo hoặc sửa (ca kiểm thử MSG-04). */
export const USERNAME_PREFIX_SERVICE_ERROR = "kt.loi";

const uid = (n: number) =>
  `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;

const slug = (text: string) =>
  text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();

function doctor(
  specialties: string[],
  qualifications: string,
  isPublic: boolean,
): DoctorInfo {
  return {
    specialties,
    qualifications,
    biography: "Hơn 10 năm khám và điều trị chấn thương chỉnh hình.",
    consultationInfo:
      "Tư vấn sáng thứ Hai đến thứ Sáu.\nMang theo phim chụp cũ nếu có.",
    isPublic,
  };
}

type DoctorSeed = Pick<
  MockStaffRecord,
  "username" | "displayName" | "photoUrl" | "status" | "doctor"
>;

const DOCTORS: DoctorSeed[] = [
  {
    username: "bs.trung",
    displayName: "BS. Nguyễn Văn Trung",
    photoUrl: "/images/home/doctor-senior-orthopedic.jpg",
    status: "ACTIVE",
    doctor: doctor(["Cột sống", "Chấn thương thể thao"], "Thạc sĩ Y khoa", true),
  },
  {
    username: "bs.minh",
    displayName: "BS. Lê Minh Quân",
    photoUrl: "/images/home/doctor-male-glasses.jpg",
    status: "ACTIVE",
    doctor: doctor(["Khớp gối"], "Bác sĩ chuyên khoa II", true),
  },
  {
    username: "bs.ha",
    displayName: "ThS.BS. Trần Thu Hà",
    photoUrl: "/images/home/doctor-female-desk.jpg",
    status: "ACTIVE",
    doctor: doctor(
      ["Phẫu thuật nội soi khớp", "Khớp vai"],
      "Thạc sĩ Y khoa",
      true,
    ),
  },
  {
    username: "bs.yen",
    displayName: "BS. Vũ Hải Yến",
    photoUrl: null,
    status: "ACTIVE",
    doctor: doctor(["Nhi chỉnh hình"], "Bác sĩ chuyên khoa I", false),
  },
  {
    username: "bs.thanh",
    displayName: "BS. Nguyễn Văn Thành",
    photoUrl: null,
    status: "ACTIVE",
    doctor: doctor(["Thay khớp"], "Tiến sĩ Y khoa", false),
  },
  {
    username: "bs.an",
    displayName: "BS. Phạm Quốc An",
    photoUrl: null,
    status: "REMOVED",
    doctor: doctor(["Bàn tay và cổ tay"], "Bác sĩ chuyên khoa I", false),
  },
];

const FIRST = [
  "An", "Bình", "Chi", "Dung", "Giang", "Hạnh", "Khoa", "Lan",
  "Mai", "Nam", "Phúc", "Quang", "Sơn", "Thảo", "Uyên",
];
const FAMILY = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng"];
const OTHER_ROLES: [StaffRole, string, string][] = [
  ["RECEPTIONIST", "le.tan", "Lễ tân"],
  ["IMAGING_TECHNICIAN", "ktv.cdha", "KTV CĐHA"],
  ["LAB_TECHNICIAN", "ktv.xn", "KTV XN"],
];

function buildProfiles(): MockStaffRecord[] {
  const records: MockStaffRecord[] = DOCTORS.map((seed, i) => ({
    ...seed,
    id: uid(100 + i),
    accountId: uid(200 + i),
    role: "ORTHOPEDIC_DOCTOR",
    workContact: `0901 234 56${i}`,
  }));
  for (let i = 0; i < 38; i += 1) {
    const [role, prefix, label] = OTHER_ROLES[i % 3];
    const first = FIRST[i % FIRST.length];
    const isHoa = i === 0;
    records.push({
      id: uid(300 + i),
      accountId: uid(400 + i),
      username: isHoa ? "le.tan.hoa" : `${prefix}.${slug(first)}${i}`,
      role,
      displayName: isHoa
        ? "Trần Thị Hoa"
        : `${label} ${FAMILY[i % FAMILY.length]} ${first}`,
      photoUrl: null,
      workContact:
        i % 2 === 0
          ? `${slug(first)}${i}@example.com`
          : `0988 ${String(100 + i)} 000`,
      status: i === 7 ? "REMOVED" : "ACTIVE",
      doctor: null,
    });
  }
  // Hồ sơ có tên đăng nhập bắt đầu bằng kt.loi: sửa luôn gặp lỗi dịch vụ.
  records.push({
    id: uid(399),
    accountId: uid(499),
    username: "kt.loi.xn",
    role: "LAB_TECHNICIAN",
    displayName: "KTV XN Đặng Lợi",
    photoUrl: null,
    workContact: "0977 000 111",
    status: "ACTIVE",
    doctor: null,
  });
  return records;
}

function buildEligible(): EligibleStaffAccount[] {
  const list: [string, string, StaffRole][] = [
    ["le.tan.minh", "Lê Minh Tân", "RECEPTIONIST"],
    ["le.tan.lan", "Đỗ Thị Lan", "RECEPTIONIST"],
    ["bs.hung", "Bùi Quốc Hùng", "ORTHOPEDIC_DOCTOR"],
    ["bs.linh", "Võ Khánh Linh", "ORTHOPEDIC_DOCTOR"],
    ["ktv.cdha.tuan", "Ngô Anh Tuấn", "IMAGING_TECHNICIAN"],
    ["ktv.cdha.vy", "Phan Tường Vy", "IMAGING_TECHNICIAN"],
    ["ktv.xn.hai", "Hà Thanh Hải", "LAB_TECHNICIAN"],
    [USERNAME_PREFIX_SERVICE_ERROR, "Đặng Văn Lợi", "LAB_TECHNICIAN"],
  ];
  return list.map(([username, fullName, role], i) => ({
    accountId: uid(500 + i),
    username,
    fullName,
    role,
  }));
}

export const mockStore: {
  profiles: MockStaffRecord[];
  accounts: EligibleStaffAccount[];
} = { profiles: buildProfiles(), accounts: buildEligible() };

/** Đưa kho giả về dữ liệu ban đầu (dùng trong test). */
export function resetStaffProfileMockStore() {
  mockStore.profiles = buildProfiles();
  mockStore.accounts = buildEligible();
}
