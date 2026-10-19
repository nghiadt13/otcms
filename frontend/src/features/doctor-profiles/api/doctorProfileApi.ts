import type { DoctorProfile } from "../types/doctorProfile";

// LỚP API GIẢ: chưa có backend nên hồ sơ bác sĩ là dữ liệu mẫu (tên không có
// thật; ảnh từ Unsplash, giấy phép Unsplash). Khi có endpoint hồ sơ công khai,
// giữ chữ ký hàm và thay phần thân bằng apiRequest (`@/lib/api/client`).
const publishedDoctors: DoctorProfile[] = [
  {
    id: "doc-tran-thu-ha",
    fullName: "Trần Thu Hà",
    degree: "ThS.BS",
    specialty: "Phẫu thuật nội soi khớp",
    focus: "Rách sụn chêm, tổn thương dây chằng khớp gối",
    yearsOfExperience: 12,
    photo: {
      src: "/images/home/doctor-female-desk.jpg",
      alt: "Bác sĩ Trần Thu Hà ngồi tại bàn khám",
    },
    featured: true,
  },
  {
    id: "doc-le-minh-quan",
    fullName: "Lê Minh Quân",
    degree: "BS.CKII",
    specialty: "Chấn thương thể thao",
    focus: "Bong gân, đứt dây chằng chéo, phục hồi sau chấn thương",
    yearsOfExperience: 9,
    photo: {
      src: "/images/home/doctor-male-glasses.jpg",
      alt: "Bác sĩ Lê Minh Quân khoanh tay, mỉm cười",
    },
    featured: true,
  },
  {
    id: "doc-nguyen-van-thanh",
    fullName: "Nguyễn Văn Thành",
    degree: "PGS.TS.BS",
    specialty: "Thay khớp nhân tạo",
    focus: "Thay khớp háng, khớp gối cho người cao tuổi",
    yearsOfExperience: 28,
    photo: {
      src: "/images/home/doctor-senior-orthopedic.jpg",
      alt: "Phó giáo sư Nguyễn Văn Thành trong phòng khám",
    },
    featured: true,
  },
  {
    id: "doc-pham-ngoc-anh",
    fullName: "Phạm Ngọc Anh",
    degree: "BS.CKI",
    specialty: "Cột sống",
    focus: "Thoát vị đĩa đệm, đau thắt lưng, cong vẹo cột sống",
    yearsOfExperience: 7,
    photo: {
      src: "/images/home/doctor-female-tablet.jpg",
      alt: "Bác sĩ Phạm Ngọc Anh xem phim chụp trên máy tính bảng",
    },
    featured: true,
  },
];

/** Bác sĩ được chọn hiện trên trang chủ (tối đa `limit` người). */
export async function listFeaturedDoctors(limit = 4): Promise<DoctorProfile[]> {
  return structuredClone(
    publishedDoctors.filter((doctor) => doctor.featured).slice(0, limit),
  );
}
