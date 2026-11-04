// Danh mục ICD-10 nhóm M (cơ xương khớp) và S (chấn thương) dùng khi chẩn đoán (R3 BR-39:
// "fixed list limited to groups M and S"). BE hiện chỉ kiểm mã đúng dạng `[MS]\d{2}(\.[A-Z0-9]{1,4})?`
// và chưa có endpoint danh mục, nên danh sách chỉnh hình hay gặp được giữ tạm ở đây.
// Khi BE có danh mục: thay hằng số bằng lời gọi API, giữ nguyên kiểu `IcdEntry`.

export type IcdGroup = "M" | "S";
export type IcdEntry = { code: string; name: string; group: IcdGroup };

const m = (code: string, name: string): IcdEntry => ({
  code,
  name,
  group: "M",
});
const s = (code: string, name: string): IcdEntry => ({
  code,
  name,
  group: "S",
});

export const ICD10_CATALOG: readonly IcdEntry[] = [
  m("M06.9", "Viêm khớp dạng thấp, không đặc hiệu"),
  m("M10.9", "Gút, không đặc hiệu"),
  m("M16.1", "Thoái hóa khớp háng nguyên phát"),
  m("M17.0", "Thoái hóa khớp gối nguyên phát hai bên"),
  m("M17.1", "Thoái hóa khớp gối nguyên phát"),
  m("M17.9", "Thoái hóa khớp gối, không đặc hiệu"),
  m("M19.9", "Thoái hóa khớp, không đặc hiệu"),
  m("M20.1", "Ngón chân cái vẹo ngoài"),
  m("M23.2", "Rối loạn sụn chêm do rách hoặc chấn thương cũ"),
  m("M23.3", "Rối loạn sụn chêm khác"),
  m("M24.2", "Rối loạn dây chằng"),
  m("M25.5", "Đau khớp"),
  m("M25.6", "Cứng khớp, chưa phân loại"),
  m("M43.1", "Trượt đốt sống"),
  m("M45", "Viêm cột sống dính khớp"),
  m("M47.8", "Thoái hóa cột sống khác"),
  m("M47.9", "Thoái hóa cột sống, không đặc hiệu"),
  m("M48.0", "Hẹp ống sống"),
  m("M50.1", "Rối loạn đĩa đệm cổ có bệnh rễ thần kinh"),
  m("M51.1", "Rối loạn đĩa đệm thắt lưng có bệnh rễ thần kinh"),
  m("M51.2", "Thoát vị đĩa đệm thắt lưng khác"),
  m("M54.2", "Đau cổ"),
  m("M54.3", "Đau thần kinh tọa"),
  m("M54.4", "Đau thắt lưng kèm đau thần kinh tọa"),
  m("M54.5", "Đau thắt lưng"),
  m("M65.3", "Ngón tay lò xo"),
  m("M65.4", "Viêm bao gân quay (De Quervain)"),
  m("M75.0", "Viêm dính bao khớp vai"),
  m("M75.1", "Hội chứng chóp xoay"),
  m("M75.4", "Hội chứng chèn ép khớp vai"),
  m("M76.6", "Viêm gân Achilles"),
  m("M77.1", "Viêm lồi cầu ngoài xương cánh tay"),
  m("M79.1", "Đau cơ"),
  m("M80.0", "Loãng xương kèm gãy xương bệnh lý"),
  m("M81.0", "Loãng xương sau mãn kinh"),
  m("M84.4", "Gãy xương bệnh lý"),
  s("S13.4", "Bong gân cột sống cổ"),
  s("S22.3", "Gãy xương sườn"),
  s("S32.0", "Gãy đốt sống thắt lưng"),
  s("S33.5", "Bong gân cột sống thắt lưng"),
  s("S42.0", "Gãy xương đòn"),
  s("S42.2", "Gãy đầu trên xương cánh tay"),
  s("S43.0", "Trật khớp vai"),
  s("S46.0", "Rách chóp xoay khớp vai"),
  s("S52.5", "Gãy đầu dưới xương quay"),
  s("S52.6", "Gãy đầu dưới xương quay và xương trụ"),
  s("S62.0", "Gãy xương thuyền bàn tay"),
  s("S62.3", "Gãy xương đốt bàn tay"),
  s("S72.0", "Gãy cổ xương đùi"),
  s("S72.1", "Gãy xương đùi vùng mấu chuyển"),
  s("S72.3", "Gãy thân xương đùi"),
  s("S76.1", "Tổn thương cơ và gân tứ đầu đùi"),
  s("S82.0", "Gãy xương bánh chè"),
  s("S82.1", "Gãy đầu trên xương chày"),
  s("S82.6", "Gãy mắt cá ngoài"),
  s("S82.8", "Gãy xương cẳng chân khác"),
  s("S83.2", "Rách sụn chêm do chấn thương"),
  s("S83.4", "Bong gân dây chằng bên khớp gối"),
  s("S83.5", "Tổn thương dây chằng chéo khớp gối"),
  s("S86.0", "Tổn thương gân Achilles"),
  s("S92.3", "Gãy xương đốt bàn chân"),
  s("S93.4", "Bong gân cổ chân"),
];

const BY_CODE = new Map(ICD10_CATALOG.map((entry) => [entry.code, entry]));

/** Dạng mã BE chấp nhận (`ClinicalExaminationServiceImpl.ICD`). */
export const ICD_CODE_PATTERN = /^[MS]\d{2}(\.[A-Za-z0-9]{1,4})?$/i;

export function findIcdEntry(code: string): IcdEntry | undefined {
  return BY_CODE.get(code.toUpperCase());
}

/** "M17.1 · Thoái hóa khớp gối nguyên phát"; mã lạ (ngoài danh mục) chỉ hiện mã. */
export function icdLabel(code: string): string {
  const entry = findIcdEntry(code);
  return entry ? `${entry.code} · ${entry.name}` : code.toUpperCase();
}

/** Tìm theo mã hoặc tên, không phân biệt hoa thường và dấu. */
export function searchIcd(query: string): readonly IcdEntry[] {
  const needle = fold(query.trim());
  if (!needle) return ICD10_CATALOG;
  return ICD10_CATALOG.filter((entry) =>
    fold(`${entry.code} ${entry.name}`).includes(needle),
  );
}

function fold(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}
