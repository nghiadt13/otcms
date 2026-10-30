import { formatDate } from "@/lib/format/formatDate";
import type { AdministrativeInfo } from "../types/examinationResult";
import { ResultFieldList } from "./ResultFieldList";

const SEX_TEXT: Record<AdministrativeInfo["sex"], string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
};

/** Khối "Thông tin hành chính" của người bệnh tại thời điểm khám. */
export function AdministrativeBlock({ info }: { info: AdministrativeInfo }) {
  return (
    <ResultFieldList
      items={[
        { label: "Họ và tên", value: info.fullName },
        { label: "Ngày sinh", value: formatDate(info.dateOfBirth) },
        { label: "Giới tính", value: SEX_TEXT[info.sex] },
        { label: "Mã bệnh nhân", value: info.patientCode },
        { label: "Số điện thoại", value: info.phone },
        { label: "Địa chỉ", value: info.address },
      ]}
    />
  );
}
