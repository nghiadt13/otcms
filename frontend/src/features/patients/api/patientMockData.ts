import type { Sex } from "../types/patient";

// Dữ liệu giả của kho hồ sơ (chỉ dùng ở lớp API giả). Có SĐT, địa chỉ, email, CCCD để kiểm tra
// tìm và trùng; lớp API không trả các trường này ra ngoài.

/** Bản ghi đầy đủ trong kho giả. */
export interface MockPatient {
  id: string;
  patientCode: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: Sex;
  hasAccount: boolean;
  phone: string;
  address: string;
  citizenId: string | null;
  email: string | null;
}

/** Id khớp mock của màn Tiếp nhận (`features/reception`): đừng đổi tên. */
export const PATIENT_IDS = {
  an: "pat-an",
  binh: "pat-binh",
  cuong: "pat-cuong",
  dung: "pat-dung",
  em: "pat-em",
  /** Cặp sinh đôi cùng SĐT và ngày sinh (kịch bản MULTIPLE). */
  bao: "pat-bao",
  khue: "pat-khue",
  /** Mẹ của An, hồ sơ gắn tài khoản của An (PARENT); 90 tuổi, demo nhóm ưu tiên ≥ 75 tuổi. */
  lan: "pat-lan",
} as const;

/** SĐT và ngày sinh chung của cặp sinh đôi. */
export const TWINS_PHONE = "0909000111";
export const TWINS_DATE_OF_BIRTH = "2019-05-20";

const SEED: MockPatient[] = [
  {
    id: PATIENT_IDS.an,
    patientCode: "BN-000123",
    fullName: "Nguyễn Văn An",
    dateOfBirth: "1992-07-21",
    sex: "MALE",
    hasAccount: true,
    phone: "0912345678",
    address: "45 Trần Phú, Hà Đông, Hà Nội",
    citizenId: "001058012345",
    email: "nguyenvanan@example.com",
  },
  {
    id: PATIENT_IDS.binh,
    patientCode: "BN-000124",
    fullName: "Trần Thị Bình",
    dateOfBirth: "1985-09-02",
    sex: "FEMALE",
    hasAccount: true,
    phone: "0987123456",
    address: "8 Đường Láng, Đống Đa, Hà Nội",
    citizenId: "001185094321",
    email: "tranthibinh@example.com",
  },
  {
    id: PATIENT_IDS.cuong,
    patientCode: "BN-000125",
    fullName: "Lê Văn Cường",
    dateOfBirth: "1971-11-20",
    sex: "MALE",
    hasAccount: false,
    phone: "0977555123",
    address: "101 Giải Phóng, Hai Bà Trưng, Hà Nội",
    citizenId: null,
    email: null,
  },
  {
    id: PATIENT_IDS.dung,
    patientCode: "BN-000126",
    fullName: "Hoàng Thị Dung",
    dateOfBirth: "1993-04-08",
    sex: "FEMALE",
    hasAccount: false,
    phone: "0933222110",
    address: "17 Kim Mã, Ba Đình, Hà Nội",
    citizenId: "001193061782",
    email: null,
  },
  {
    id: PATIENT_IDS.em,
    patientCode: "BN-000127",
    fullName: "Bùi Thị Em",
    dateOfBirth: "2001-12-30",
    sex: "FEMALE",
    hasAccount: true,
    phone: "0966112233",
    address: "5 Tôn Thất Tùng, Đống Đa, Hà Nội",
    citizenId: "001201009876",
    email: "buithiem@example.com",
  },
  {
    id: PATIENT_IDS.bao,
    patientCode: "BN-000201",
    fullName: "Trần Gia Bảo",
    dateOfBirth: TWINS_DATE_OF_BIRTH,
    sex: "MALE",
    hasAccount: false,
    phone: TWINS_PHONE,
    address: "31 Phố Huế, Hai Bà Trưng, Hà Nội",
    citizenId: null,
    email: null,
  },
  {
    id: PATIENT_IDS.khue,
    patientCode: "BN-000202",
    fullName: "Trần Ngọc Khuê",
    dateOfBirth: TWINS_DATE_OF_BIRTH,
    sex: "FEMALE",
    hasAccount: false,
    phone: TWINS_PHONE,
    address: "31 Phố Huế, Hai Bà Trưng, Hà Nội",
    citizenId: null,
    email: null,
  },
  {
    // Cùng mã BN-000456 với hồ sơ PARENT của An trong lib/patient-scope (không có tài khoản riêng).
    id: PATIENT_IDS.lan,
    patientCode: "BN-000456",
    fullName: "Nguyễn Thị Lan",
    dateOfBirth: "1936-03-14",
    sex: "FEMALE",
    hasAccount: false,
    phone: "0934567891",
    address: "45 Trần Phú, Hà Đông, Hà Nội",
    citizenId: null,
    email: null,
  },
];

/** Bản sao mới của kho ban đầu; mỗi lần đặt lại dùng một bản sao riêng. */
export function createInitialPatients(): MockPatient[] {
  return structuredClone(SEED);
}
