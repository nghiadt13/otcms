import { describe, expect, it } from "vitest";
import {
  buildAppointmentQuery,
  parseAppointmentUrl,
  rangeErrorCode,
  resolveAvailabilityRange,
  resolveListRange,
} from "./appointmentUrlRules";

const parse = (query: string) =>
  parseAppointmentUrl(new URLSearchParams(query));

describe("parseAppointmentUrl", () => {
  it("mặc định tab danh sách; tab lạ về danh sách", () => {
    expect(parse("").tab).toBe("list");
    expect(parse("tab=zzz").tab).toBe("list");
    expect(parse("tab=availability").tab).toBe("availability");
  });

  it("date thành from = to", () => {
    expect(parse("date=2026-10-21")).toMatchObject({
      from: "2026-10-21",
      to: "2026-10-21",
    });
  });

  it("thiếu to thì to = from; thiếu from thì from = to", () => {
    expect(parse("from=2026-10-21")).toMatchObject({
      from: "2026-10-21",
      to: "2026-10-21",
    });
    expect(parse("to=2026-10-25")).toMatchObject({
      from: "2026-10-25",
      to: "2026-10-25",
    });
  });

  it("bỏ qua ngày sai và trạng thái sai", () => {
    expect(parse("from=2026-13-40&status=XYZ")).toMatchObject({
      from: undefined,
      status: undefined,
    });
  });

  it("tab giờ trống cắt khoảng dài hơn 7 ngày còn from + 6", () => {
    expect(parse("tab=availability&from=2026-10-20&to=2026-10-30").to).toBe(
      "2026-10-26",
    );
    expect(parse("from=2026-10-20&to=2026-10-30").to).toBe("2026-10-30");
  });

  it("giữ nguyên from > to để màn báo MSG-54", () => {
    const state = parse("from=2026-10-25&to=2026-10-21");
    expect(rangeErrorCode(state.from, state.to)).toBe("MSG-54");
    expect(rangeErrorCode("2026-10-21", "2026-10-21")).toBeNull();
  });
});

describe("buildAppointmentQuery", () => {
  it("không ghi mặc định, không bao giờ có q hay page", () => {
    expect(buildAppointmentQuery({ tab: "list" })).toBe("");
    const query = buildAppointmentQuery({
      tab: "availability",
      doctorId: "d1",
      from: "2026-10-21",
      to: "2026-10-21",
      status: "BOOKED",
    });
    expect(query).toBe(
      "?tab=availability&doctorId=d1&from=2026-10-21&to=2026-10-21&status=BOOKED",
    );
    expect(query).not.toMatch(/q=|page=/);
  });
});

describe("resolve ranges", () => {
  it("danh sách: mặc định hôm nay → +7; có from thì to mặc định = from", () => {
    expect(resolveListRange({}, "2026-10-20")).toEqual({
      from: "2026-10-20",
      to: "2026-10-27",
    });
    expect(resolveListRange({ from: "2026-10-22" }, "2026-10-20")).toEqual({
      from: "2026-10-22",
      to: "2026-10-22",
    });
  });

  it("giờ trống: mặc định một ngày, tối đa 7 ngày", () => {
    expect(resolveAvailabilityRange({}, "2026-10-20")).toEqual({
      from: "2026-10-20",
      to: "2026-10-20",
    });
    expect(
      resolveAvailabilityRange({ from: "2026-10-20", to: "2026-11-30" }, "x")
        .to,
    ).toBe("2026-10-26");
  });
});
