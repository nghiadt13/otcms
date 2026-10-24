import type { ReactNode } from "react";
import type {
  ExaminationResult,
  ResultBlock,
} from "../types/examinationResult";
import { AdministrativeBlock } from "./AdministrativeBlock";
import { ExaminationBlock } from "./ExaminationBlock";
import { ImagingBlock } from "./ImagingBlock";
import { LaboratoryBlock } from "./LaboratoryBlock";
import { PrescriptionsBlock } from "./PrescriptionsBlock";
import { ResultSection } from "./ResultSection";
import { ResultSheetBlock } from "./ResultSheetBlock";

export interface ResultSectionView {
  id: string;
  title: string;
  /** Nhãn ngắn cho mục lục. */
  shortTitle: string;
  body: ReactNode;
}

/**
 * Các khối hiển thị của một lượt, theo thứ tự cố định. Một khối chỉ vào danh sách khi
 * BE liệt kê nó trong `visibleBlocks` VÀ có dữ liệu; thiếu một trong hai thì không có
 * cả tiêu đề lẫn nội dung.
 */
export function buildResultSections(
  result: ExaminationResult,
): ResultSectionView[] {
  const visible = new Set<ResultBlock>(result.visibleBlocks);
  const candidates: { block: ResultBlock; view: Omit<ResultSectionView, "body">; body: ReactNode }[] = [
    {
      block: "EXAMINATION",
      view: { id: "kham", title: "Kết quả khám", shortTitle: "Khám" },
      body: result.examination && (
        <ExaminationBlock examination={result.examination} />
      ),
    },
    {
      block: "IMAGING",
      view: { id: "hinh-anh", title: "Hình ảnh", shortTitle: "Hình ảnh" },
      body: result.imaging && <ImagingBlock items={result.imaging} />,
    },
    {
      block: "LABORATORY",
      view: { id: "xet-nghiem", title: "Xét nghiệm", shortTitle: "Xét nghiệm" },
      body: result.laboratory && <LaboratoryBlock groups={result.laboratory} />,
    },
    {
      block: "PRESCRIPTIONS",
      view: { id: "don-thuoc", title: "Đơn thuốc", shortTitle: "Đơn thuốc" },
      body: result.prescriptions && (
        <PrescriptionsBlock items={result.prescriptions} />
      ),
    },
    {
      block: "ADMINISTRATIVE",
      view: {
        id: "hanh-chinh",
        title: "Thông tin hành chính",
        shortTitle: "Hành chính",
      },
      body: result.administrative && (
        <AdministrativeBlock info={result.administrative} />
      ),
    },
    {
      block: "RESULT_SHEET",
      view: {
        id: "phieu-ket-qua",
        title: "Phiếu kết quả",
        shortTitle: "Phiếu kết quả",
      },
      body: result.resultSheets && (
        <ResultSheetBlock sheets={result.resultSheets} />
      ),
    },
  ];
  return candidates
    .filter(({ block, body }) => visible.has(block) && Boolean(body))
    .map(({ view, body }) => ({ ...view, body }));
}

/** Các khối của lượt khám, mỗi khối một mục h2 có id neo cho mục lục. */
export function ExaminationResultBlocks({
  sections,
}: {
  sections: ResultSectionView[];
}) {
  return sections.map((section) => (
    <ResultSection key={section.id} id={section.id} title={section.title}>
      {section.body}
    </ResultSection>
  ));
}
