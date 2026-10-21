import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { startOfWeek } from "../utils/shiftWeekRange";
import {
  createShift,
  deleteShift,
  listAffectedAppointments,
  listRoomOptions,
  listShifts,
  listStaffOptions,
  mockTomorrow,
  resetShiftMockStore,
  updateShift,
} from "./shiftApi";
import { shiftMockConfig } from "./shiftMockData";

const today = todayIsoDate();
const tomorrow = mockTomorrow();

async function codeOf(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError);
    return {
      status: (error as ApiError).status,
      code: (error as ApiError).code,
    };
  }
  throw new Error("Lẽ ra phải ném ApiError");
}

describe("shiftApi", () => {
  beforeEach(() => {
    shiftMockConfig.delayMs = 0;
    resetShiftMockStore();
  });

  it("listShifts trả { items } đúng hình, lọc theo kỳ và vai trò", async () => {
    const from = addDays(today, -8);
    const to = addDays(today, 7);
    const { items } = await listShifts({ from, to });
    expect(items.length).toBeGreaterThan(5);
    expect(items[0]).toEqual(
      expect.objectContaining({
        id: expect.any(String),
        date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        startTime: expect.stringMatching(/^\d{2}:\d{2}$/),
        roomCategory: expect.any(String),
        allowedActions: expect.any(Array),
      }),
    );
    const dates = items.map((s) => s.date);
    expect(dates).toEqual([...dates].sort());
    const past = items.find((s) => s.id === "sh-past-bs1");
    expect(past?.allowedActions).toEqual(["view"]);
    const doctors = await listShifts({ from, to, role: "ORTHOPEDIC_DOCTOR" });
    expect(doctors.items.every((s) => s.role === "ORTHOPEDIC_DOCTOR")).toBe(
      true,
    );
    const empty = await listShifts({
      from: addDays(today, 60),
      to: addDays(today, 66),
    });
    expect(empty.items).toEqual([]);
  });

  it("listShifts lỗi: staffId không hợp lệ 404 MSG-33, from mốc test 503 MSG-04, from > to 400 MSG-54", async () => {
    const from = startOfWeek(today);
    const to = addDays(from, 6);
    expect(await codeOf(listShifts({ from, to, staffId: "st-off" }))).toEqual({
      status: 404,
      code: "MSG-33",
    });
    expect(
      await codeOf(listShifts({ from, to, roomId: "rm-khong-co" })),
    ).toEqual({ status: 404, code: "MSG-33" });
    expect(
      await codeOf(listShifts({ from: "2030-01-07", to: "2030-01-13" })),
    ).toEqual({ status: 503, code: "MSG-04" });
    expect(await codeOf(listShifts({ from: to, to: from }))).toEqual({
      status: 400,
      code: "MSG-54",
    });
  });

  it("createShift trả ca mới có allowedActions; không đủ điều kiện thì ném lỗi theo mã", async () => {
    const created = await createShift({
      staffId: "st-cdha",
      date: addDays(today, 4),
      startTime: "13:00",
      endTime: "16:00",
      roomId: "rm-cdha1",
    });
    expect(created).toEqual(
      expect.objectContaining({
        staffName: "Lê Quang Bình",
        roomName: "Phòng CĐHA 1",
        allowedActions: ["edit", "delete"],
      }),
    );
    const base = {
      staffId: "st-bs1",
      date: tomorrow,
      startTime: "08:00",
      endTime: "12:00",
      roomId: "rm-k1",
    };
    // Trùng ca của chính nhân viên.
    expect(await codeOf(createShift(base))).toEqual({
      status: 409,
      code: "MSG-35",
    });
    // Bác sĩ thứ hai vào phòng khám đã có bác sĩ khác trong khung giờ.
    expect(
      await codeOf(
        createShift({
          ...base,
          staffId: "st-bs1",
          date: addDays(today, 2),
          startTime: "13:30",
          endTime: "16:00",
          roomId: "rm-k1",
        }),
      ),
    ).toEqual({ status: 409, code: "MSG-35" });
    // Nhân viên không đủ điều kiện.
    expect(
      await codeOf(
        createShift({
          ...base,
          staffId: "st-off",
          date: addDays(today, 5),
          roomId: "rm-cdha1",
        }),
      ),
    ).toEqual({ status: 409, code: "MSG-35" });
  });

  it("createShift: phòng bảo trì, kết thúc <= bắt đầu, ngày quá khứ, lễ tân -> 400 MSG-34 kèm fieldErrors", async () => {
    const base = {
      staffId: "st-xn",
      date: addDays(today, 5),
      startTime: "08:00",
      endTime: "11:00",
      roomId: "rm-xn1",
    };
    expect(await codeOf(createShift(base))).toEqual({
      status: 400,
      code: "MSG-34",
    });
    await expect(
      createShift({ ...base, roomId: "rm-cdha1", endTime: "08:00" }),
    ).rejects.toMatchObject({
      fieldErrors: [{ field: "endTime", code: "MSG-34" }],
    });
    expect(
      await codeOf(
        createShift({ ...base, roomId: "rm-cdha1", date: addDays(today, -1) }),
      ),
    ).toEqual({ status: 400, code: "MSG-34" });
    expect(
      await codeOf(
        createShift({ ...base, staffId: "st-lt1", roomId: "rm-k1" }),
      ),
    ).toEqual({ status: 400, code: "MSG-34" });
  });

  it("updateShift đổi giờ/phòng, giữ nhân viên; ca bác sĩ có lịch hẹn -> 409 MSG-36", async () => {
    const updated = await updateShift("sh-bs2-pm", {
      date: addDays(today, 2),
      startTime: "14:00",
      endTime: "18:00",
      roomId: "rm-k2",
    });
    expect(updated).toEqual(
      expect.objectContaining({
        id: "sh-bs2-pm",
        staffId: "st-bs2",
        startTime: "14:00",
        roomName: "Phòng khám 2",
      }),
    );
    expect(
      await codeOf(
        updateShift("sh-bs1-am", {
          date: tomorrow,
          startTime: "08:30",
          endTime: "12:00",
          roomId: "rm-k2",
        }),
      ),
    ).toEqual({ status: 409, code: "MSG-36" });
    expect(
      await codeOf(
        updateShift("khong-co", {
          date: tomorrow,
          startTime: "08:00",
          endTime: "09:00",
          roomId: "rm-k2",
        }),
      ),
    ).toEqual({ status: 404, code: "MSG-33" });
    expect(
      await codeOf(
        updateShift("sh-past-bs1", {
          date: tomorrow,
          startTime: "08:00",
          endTime: "09:00",
          roomId: "rm-k2",
        }),
      ),
    ).toEqual({ status: 409, code: "MSG-33" });
  });

  it("deleteShift: thành công, ca bác sĩ có lịch hẹn MSG-36, ca đã qua MSG-33, không còn MSG-33", async () => {
    await expect(deleteShift("sh-cdha-am")).resolves.toBeUndefined();
    expect(await codeOf(deleteShift("sh-cdha-am"))).toEqual({
      status: 404,
      code: "MSG-33",
    });
    expect(await codeOf(deleteShift("sh-bs1-am"))).toEqual({
      status: 409,
      code: "MSG-36",
    });
    expect(await codeOf(deleteShift("sh-past-cdha"))).toEqual({
      status: 409,
      code: "MSG-33",
    });
  });

  it("listAffectedAppointments: 3 lịch hẹn cho sh-bs1-am, 0 cho ca khác, 404 khi không còn", async () => {
    const affected = await listAffectedAppointments("sh-bs1-am");
    expect(affected.totalItems).toBe(3);
    expect(affected.items).toHaveLength(3);
    expect(affected.items[0].startAt).toContain(tomorrow);
    expect((await listAffectedAppointments("sh-bs2-am")).totalItems).toBe(0);
    expect(await codeOf(listAffectedAppointments("khong-co"))).toEqual({
      status: 404,
      code: "MSG-33",
    });
  });

  it("lookup: staff-options chỉ nhân viên đủ điều kiện, room-options chỉ phòng Sẵn sàng", async () => {
    const staff = await listStaffOptions();
    expect(staff.items.map((s) => s.staffId)).not.toContain("st-off");
    expect(staff.items).toHaveLength(5);
    expect((await listStaffOptions("ORTHOPEDIC_DOCTOR")).items).toHaveLength(2);
    const rooms = await listRoomOptions();
    expect(rooms.items.map((r) => r.roomId)).not.toContain("rm-xn1");
    expect((await listRoomOptions("IMAGING")).items).toEqual([
      { roomId: "rm-cdha1", name: "Phòng CĐHA 1", category: "IMAGING" },
    ]);
  });
});
