// Kho giả dùng chung giữa các feature: chỉ cho lớp API giả, xóa khi nối BE; không chứa luật nghiệp vụ.
// Hồ sơ tạo ở UC10 (feature `patients`) được đăng ký vào đây để màn Tiếp nhận (feature `reception`)
// tra cứu được mã `pat-new-N`. Chỉ giữ các trường nhận dạng; SĐT và địa chỉ đầy đủ ở kho của `patients`.

/** Thông tin nhận dạng tối thiểu của một bệnh nhân trong kho giả dùng chung. */
export interface MockPatientIdentity {
  id: string;
  patientCode: string;
  fullName: string;
  /** yyyy-MM-dd */
  dateOfBirth: string;
  sex: "MALE" | "FEMALE";
  phone: string;
}

const directory = new Map<string, MockPatientIdentity>();

/** Ghi hoặc thay hồ sơ theo `id`. Lưu bản sao để bên gọi sửa đối tượng không đổi kho. */
export function registerMockPatient(patient: MockPatientIdentity): void {
  directory.set(patient.id, { ...patient });
}

/** Tra hồ sơ theo `id`; trả bản sao, hoặc `undefined` khi chưa đăng ký. */
export function findMockPatient(id: string): MockPatientIdentity | undefined {
  const found = directory.get(id);
  return found ? { ...found } : undefined;
}
