import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  changeRoomStatus,
  createRoom,
  getRoom,
  listRoomServiceOptions,
  listRooms,
  resetRoomMockStore,
  updateRoom,
} from "./roomApi";
import { MOCK_MISSING_ROOM_ID, MOCK_SERVICES } from "./roomMockData";

const emptyFilters = { q: "", type: "", status: "", serviceId: "" } as const;
const list = (over: Partial<Parameters<typeof listRooms>[0]> = {}) =>
  listRooms({ ...emptyFilters, ...over });

async function expectApiError(
  promise: Promise<unknown>,
  status: number,
  code: string,
) {
  const err = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(err).toBeInstanceOf(ApiError);
  expect((err as ApiError).status).toBe(status);
  expect((err as ApiError).code).toBe(code);
  return err as ApiError;
}

const xray = MOCK_SERVICES.find((s) => s.name === "X-quang")!;
const exam = MOCK_SERVICES.find((s) => s.name === "Khám chỉnh hình")!;

describe("roomApi", () => {
  beforeEach(() => resetRoomMockStore());

  it("23 phòng, 2 trang, sắp theo tên", async () => {
    const page0 = await list();
    expect(page0.totalItems).toBe(23);
    expect(page0.totalPages).toBe(2);
    expect(page0.items).toHaveLength(20);
    expect((await list({ page: 1 })).items).toHaveLength(3);
  });

  it("lọc theo q, type, status, serviceId", async () => {
    expect(
      (await list({ q: "p10" })).items.every((r) => r.name.includes("P10")),
    ).toBe(true);
    expect((await list({ type: "IMAGING" })).totalItems).toBe(8);
    expect((await list({ status: "UNAVAILABLE" })).totalItems).toBe(3);
    const byService = await list({ serviceId: xray.id });
    expect(byService.items.every((r) => r.type === "IMAGING")).toBe(true);
    expect((await list({ q: "khongco" })).items).toEqual([]);
  });

  it("lọc sai enum → 400 MSG-54; q đặc biệt → 403 / 500", async () => {
    await expectApiError(list({ type: "PROCEDURE" as never }), 400, "MSG-54");
    await expectApiError(list({ q: "khong-quyen" }), 403, "MSG-50");
    await expectApiError(list({ q: "loi-may-chu" }), 500, "MSG-04");
  });

  it("service-options đúng loại; loại sai → 400", async () => {
    const imaging = await listRoomServiceOptions("IMAGING");
    expect(imaging).toHaveLength(3);
    expect(imaging.every((o) => o.category === "IMAGING")).toBe(true);
    expect(await listRoomServiceOptions()).toHaveLength(9);
    await expectApiError(listRoomServiceOptions("PROCEDURE"), 400, "MSG-54");
  });

  it("tạo phòng 201: AVAILABLE, đủ allowedActions", async () => {
    const room = await createRoom({
      name: "P201",
      type: "EXAMINATION",
      description: "Mới",
      serviceIds: [exam.id],
    });
    expect(room.status).toBe("AVAILABLE");
    expect(room.allowedActions).toEqual(["edit", "changeStatus"]);
    expect((await getRoom(room.id)).name).toBe("P201");
  });

  it("tên trùng (không phân biệt hoa thường) → 409 MSG-98 trên name", async () => {
    const err = await expectApiError(
      createRoom({
        name: "p101",
        type: "EXAMINATION",
        description: "",
        serviceIds: [exam.id],
      }),
      409,
      "MSG-98",
    );
    expect(err.fieldErrors).toEqual([{ field: "name", code: "MSG-98" }]);
  });

  it("dịch vụ sai loại → 400 MSG-62 trên serviceIds", async () => {
    const err = await expectApiError(
      createRoom({
        name: "P202",
        type: "EXAMINATION",
        description: "",
        serviceIds: [xray.id],
      }),
      400,
      "MSG-62",
    );
    expect(err.fieldErrors?.[0].field).toBe("serviceIds");
  });

  it("PUT chỉ đổi mô tả và dịch vụ, giữ tên và loại", async () => {
    const before = (await list({ q: "P101" })).items[0];
    const after = await updateRoom(before.id, {
      description: "Mô tả mới",
      serviceIds: [exam.id],
    });
    expect(after.description).toBe("Mô tả mới");
    expect(after.supportedServices.map((s) => s.id)).toEqual([exam.id]);
    expect(after.name).toBe(before.name);
    expect(after.type).toBe(before.type);
  });

  it("đổi trạng thái; phòng đang dùng → 409 MSG-64 và giữ status cũ", async () => {
    const p101 = (await list({ q: "P101" })).items[0];
    expect((await changeRoomStatus(p101.id, "UNAVAILABLE")).status).toBe(
      "UNAVAILABLE",
    );

    const busy = (await list({ q: "CĐHA-02" })).items[0];
    await expectApiError(
      changeRoomStatus(busy.id, "UNAVAILABLE"),
      409,
      "MSG-64",
    );
    expect((await getRoom(busy.id)).status).toBe("AVAILABLE");
  });

  it("id không tồn tại → 404 MSG-50", async () => {
    await expectApiError(getRoom(MOCK_MISSING_ROOM_ID), 404, "MSG-50");
    await expectApiError(
      updateRoom(MOCK_MISSING_ROOM_ID, {
        description: "",
        serviceIds: [exam.id],
      }),
      404,
      "MSG-50",
    );
    await expectApiError(
      changeRoomStatus(MOCK_MISSING_ROOM_ID, "AVAILABLE"),
      404,
      "MSG-50",
    );
  });
});
