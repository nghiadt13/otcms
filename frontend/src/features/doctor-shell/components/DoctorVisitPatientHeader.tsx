import { Card, CardContent } from "@/components/ui/card";

// Thông tin tối thiểu của bệnh nhân trong workspace khám (R3 màn 9, BR-13): họ tên,
// tuổi, giới tính, mã bệnh nhân, dị ứng, tiền sử; không có số điện thoại hay địa chỉ.
// Chưa có dữ liệu thật: khi BE khám bệnh xong, thay các ô "Chờ dữ liệu" bằng dữ liệu lượt khám.
const FIELDS = [
  "Họ tên",
  "Tuổi",
  "Giới tính",
  "Mã bệnh nhân",
  "Dị ứng",
  "Tiền sử bệnh",
] as const;

export function DoctorVisitPatientHeader({ visitId }: { visitId: string }) {
  return (
    <Card className="mb-6" aria-label={`Thông tin bệnh nhân của lượt khám ${visitId}`}>
      <CardContent className="grid grid-cols-2 gap-x-6 gap-y-3 py-4 sm:grid-cols-3 lg:grid-cols-6">
        {FIELDS.map((label) => (
          <div key={label} className="min-w-0">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="truncate text-sm text-muted-foreground">Chờ dữ liệu</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
