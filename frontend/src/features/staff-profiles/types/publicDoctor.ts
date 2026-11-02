// Hồ sơ bác sĩ công khai (BR-09): chỉ thông tin chuyên môn của bác sĩ đang hoạt động và đã
// bật hiển thị. Danh sách không có `availability`; chi tiết có tóm tắt giờ nhận 7 ngày.

export type PublicDoctor = {
  id: string;
  fullName: string;
  photoUrl: string | null;
  specialties: string[];
  qualifications: string;
  biography: string;
  consultationInfo: string;
};

/** Một ngày trong 7 ngày tới: `hasOpenTime` false nghĩa là "Hết giờ" (không liệt kê từng giờ). */
export type DoctorAvailabilityDay = {
  /** YYYY-MM-DD theo giờ Việt Nam. */
  date: string;
  hasOpenTime: boolean;
};

export type PublicDoctorDetail = PublicDoctor & {
  availability: DoctorAvailabilityDay[];
};
