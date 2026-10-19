// Đánh giá đã công khai của bệnh nhân (UC62–UC63). Tên chỉ ghi viết tắt để bảo
// vệ danh tính; `clinicReply` chỉ có khi đánh giá còn công khai (BR-60).
export type PatientReview = {
  id: string;
  rating: 1 | 2 | 3 | 4 | 5;
  content: string;
  authorLabel: string;
  serviceName: string;
  clinicReply?: string;
};
