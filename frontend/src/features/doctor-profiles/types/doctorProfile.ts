// Hồ sơ bác sĩ đã công khai (BR-13): chỉ thông tin chuyên môn, không có dữ liệu
// tài khoản hay nhân sự. Ảnh là đường dẫn trong `public/`.
export type DoctorProfile = {
  id: string;
  fullName: string;
  // Học hàm, học vị ghi trước tên, ví dụ "ThS.BS", "BS.CKII".
  degree: string;
  specialty: string;
  focus: string;
  yearsOfExperience: number;
  photo: { src: string; alt: string };
  featured: boolean;
};
