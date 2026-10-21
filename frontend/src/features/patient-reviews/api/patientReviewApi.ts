import type { PatientReview } from "../types/patientReview";

// LỚP API GIẢ: đánh giá là dữ liệu mẫu, không phải nhận xét thật của bệnh nhân.
// Khi có endpoint đánh giá công khai, giữ chữ ký hàm và thay phần thân.
const publishedReviews: PatientReview[] = [
  {
    id: "rv-knee-arthroscopy",
    rating: 5,
    content:
      "Đau gối gần một năm, đi nhiều nơi chưa rõ nguyên nhân. Ở đây bác sĩ cho chụp MRI, chỉ trên phim chỗ rách sụn chêm và giải thích từng lựa chọn. Sau nội soi ba tháng tôi đã leo cầu thang bình thường.",
    authorLabel: "Chị L.T.H., 54 tuổi",
    serviceName: "Nội soi khớp gối",
    clinicReply:
      "Cảm ơn chị đã tin tưởng. Chúc chị giữ đều bài tập phục hồi, hẹn gặp lại ở lần tái khám sau sáu tháng.",
  },
  {
    id: "rv-xray",
    rating: 5,
    content:
      "Đặt lịch trên web xong là có giờ cụ thể, đến nơi chờ chưa tới 15 phút. Phim X-quang xem lại được ngay trên điện thoại.",
    authorLabel: "Anh P.Q.D., 31 tuổi",
    serviceName: "Chụp X-quang kỹ thuật số",
  },
  {
    id: "rv-physiotherapy",
    rating: 4,
    content:
      "Kỹ thuật viên theo sát, buổi nào cũng ghi lại tiến triển. Mong phòng khám mở thêm khung giờ buổi tối.",
    authorLabel: "Bác N.V.S., 67 tuổi",
    serviceName: "Vật lý trị liệu phục hồi",
  },
];

export async function listFeaturedReviews(limit = 3): Promise<PatientReview[]> {
  return structuredClone(publishedReviews.slice(0, limit));
}
