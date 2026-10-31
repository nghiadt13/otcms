import type { MsgCode } from "@/lib/api/apiError";
import { parseDecimal, type ServiceFormDraft } from "./serviceFormDraft";

// Validator thuần cho form dịch vụ. Key theo path: `name`, `price`, `modality`,
// `bookingDetails.slotMinutes`, `indicators`, `indicators[2].refMale.max`.
// Mọi lỗi dùng MSG-66 (spec 07).

export type FieldErrorMap = Record<string, MsgCode>;

const INVALID: MsgCode = "MSG-66";
export const MIN_SLOT_MINUTES = 10;

type RangeKey = "refMale" | "refFemale";

function validateRange(
  draft: ServiceFormDraft["indicators"][number],
  key: RangeKey,
  prefix: string,
  errors: FieldErrorMap,
) {
  const min = parseDecimal(draft[key].min);
  const max = parseDecimal(draft[key].max);
  if (Number.isNaN(min)) errors[`${prefix}.${key}.min`] = INVALID;
  if (Number.isNaN(max)) errors[`${prefix}.${key}.max`] = INVALID;
  // Lỗi "từ > đến" đặt ở ô "đến".
  if (!Number.isNaN(min) && !Number.isNaN(max) && min > max) {
    errors[`${prefix}.${key}.max`] = INVALID;
  }
}

/** `mode` = "create" thì bắt buộc đã chọn loại. */
export function validateServiceForm(
  draft: ServiceFormDraft,
  mode: "create" | "edit",
): FieldErrorMap {
  const errors: FieldErrorMap = {};
  if (mode === "create" && !draft.type) errors.type = INVALID;
  if (draft.name.trim() === "") errors.name = INVALID;
  if (!/^\d+$/.test(draft.price.trim())) errors.price = INVALID;

  if (draft.type === "IMAGING" && !draft.modality) errors.modality = INVALID;

  if (draft.type === "EXAMINATION") {
    const slot = draft.bookingDetails.slotMinutes.trim();
    if (
      slot !== "" &&
      (!/^\d+$/.test(slot) || Number(slot) < MIN_SLOT_MINUTES)
    ) {
      errors["bookingDetails.slotMinutes"] = INVALID;
    }
  }

  if (draft.type === "LABORATORY") {
    if (draft.indicators.length === 0) errors.indicators = INVALID;
    draft.indicators.forEach((row, index) => {
      const prefix = `indicators[${index}]`;
      if (row.name.trim() === "") errors[`${prefix}.name`] = INVALID;
      if (row.unit.trim() === "") errors[`${prefix}.unit`] = INVALID;
      validateRange(row, "refMale", prefix, errors);
      validateRange(row, "refFemale", prefix, errors);
    });
  }
  return errors;
}
