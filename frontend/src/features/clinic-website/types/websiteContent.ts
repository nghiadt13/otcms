// Nội dung website do Quản trị viên đăng (BR-61, UC73–UC74). Khách và bệnh nhân
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

/** Trang Giới thiệu đang công khai (`/gioi-thieu`); body là Markdown. */
export type PublicAboutPage = {
  title: string;
  body: string;
};

/** Bài viết sức khỏe đang công khai (`/bai-viet/{id}`); body là Markdown. */
export type PublicHealthArticle = {
  id: string;
  title: string;
  body: string;
  /** ISO có offset +07:00: ngày đăng lần đầu. */
  publishedAt: string;
};
