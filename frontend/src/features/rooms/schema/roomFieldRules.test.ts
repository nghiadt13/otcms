import { describe, expect, it } from "vitest";
import type { RoomServiceOption } from "../types/room";
import { keepServiceIdsOfCategory, validateRoomForm } from "./roomFieldRules";

const options: RoomServiceOption[] = [
  { id: "a", name: "Khám", category: "EXAMINATION" },
  { id: "b", name: "X-quang", category: "IMAGING" },
];

describe("validateRoomForm", () => {
  const empty = {
    name: "",
    type: "" as const,
    description: "",
    serviceIds: [],
  };

  it("tạo: tên, loại, dịch vụ trống đều báo MSG-62", () => {
    expect(validateRoomForm(empty, "create")).toEqual({
      name: "MSG-62",
      type: "MSG-62",
      serviceIds: "MSG-62",
    });
  });

  it("tên chỉ có khoảng trắng coi là trống", () => {
    expect(
      validateRoomForm(
        { ...empty, name: "   ", type: "IMAGING", serviceIds: ["b"] },
        "create",
      ),
    ).toEqual({ name: "MSG-62" });
  });

  it("sửa: bỏ qua tên và loại, vẫn đòi ít nhất 1 dịch vụ", () => {
    expect(validateRoomForm(empty, "edit")).toEqual({ serviceIds: "MSG-62" });
    expect(validateRoomForm({ ...empty, serviceIds: ["a"] }, "edit")).toEqual(
      {},
    );
  });
});

describe("keepServiceIdsOfCategory", () => {
  it("bỏ id không thuộc loại mới", () => {
    expect(keepServiceIdsOfCategory(["a", "b"], options, "IMAGING")).toEqual([
      "b",
    ]);
    expect(keepServiceIdsOfCategory(["a", "b"], options, "LABORATORY")).toEqual(
      [],
    );
  });
});
