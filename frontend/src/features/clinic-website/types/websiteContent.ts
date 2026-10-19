// Nội dung website do Quản trị viên đăng (BR-68, UC73–UC74). Khách và bệnh nhân
// chỉ thấy bản đang được công khai, nên kiểu này mô tả đúng bản công khai đó.
export type ClinicContact = {
  clinicName: string;
  address: string;
  hotline: string;
  email: string;
  // Mỗi dòng là một khung giờ, ví dụ "Thứ 2 – Thứ 6: 7:30 – 17:00".
  openingHours: string[];
};

export type HomeHeroContent = {
  title: string;
  description: string;
  // Ba điểm mạnh ngắn hiện dưới nút, mỗi dòng một ý.
  highlights: string[];
};

export type WebsiteContent = {
  contact: ClinicContact;
  hero: HomeHeroContent;
};
