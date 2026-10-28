// Thông báo công khai (BR-61): chỉ thông báo gửi cho nhóm "Khách và bệnh nhân". Trang công
// khai không có người đăng, kênh gửi hay trạng thái giao, nên kiểu này chỉ giữ phần hiển thị.
export type PublicAnnouncement = {
  id: string;
  title: string;
  /** Văn bản thuần, xuống dòng theo `\n`; không phải Markdown. */
  body: string;
  /** ISO có offset +07:00. */
  publishedAt: string;
};
