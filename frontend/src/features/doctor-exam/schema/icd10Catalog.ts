// Kiểu chẩn đoán ICD-10 nhóm M (cơ xương khớp) và S (chấn thương), BR-39.
// Danh mục đầy đủ lấy từ BE (GET /api/v1/clinical/icd10-codes); FE không giữ bản sao nào của danh mục.

export type IcdGroup = "M" | "S";
export type IcdEntry = { code: string; name: string; group: IcdGroup };

/** Dạng mã BE chấp nhận (`ClinicalExaminationServiceImpl.ICD`); việc mã thuộc danh mục do BE kiểm tra (MSG-39). */
export const ICD_CODE_PATTERN = /^[MS]\d{2}(\.[A-Za-z0-9]{1,4})?$/i;
