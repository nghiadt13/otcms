import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { MsgCode } from "@/lib/api/apiError";
import type { RoomServiceOption } from "../types/room";

export type RoomFormValues = {
  name: string;
  type: ServiceCategory | "";
  description: string;
  serviceIds: string[];
};

export type RoomFormErrors = Partial<
  Record<"name" | "type" | "serviceIds", MsgCode>
>;

/**
 * Kiểm tra form phòng. Ô bắt buộc trống (tên, loại, dịch vụ) dùng MSG-62 dưới ô.
 * Khi sửa, tên và loại chỉ đọc nên không kiểm tra.
 */
export function validateRoomForm(
  values: RoomFormValues,
  mode: "create" | "edit",
): RoomFormErrors {
  const errors: RoomFormErrors = {};
  if (mode === "create") {
    if (!values.name.trim()) errors.name = "MSG-62";
    if (!values.type) errors.type = "MSG-62";
  }
  if (values.serviceIds.length === 0) errors.serviceIds = "MSG-62";
  return errors;
}

/** Đổi loại phòng: chỉ giữ dịch vụ còn thuộc loại mới (BR-57). */
export function keepServiceIdsOfCategory(
  ids: string[],
  options: RoomServiceOption[],
  category: ServiceCategory,
): string[] {
  const valid = new Set(
    options.filter((o) => o.category === category).map((o) => o.id),
  );
  return ids.filter((id) => valid.has(id));
}
