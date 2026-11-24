import { describe, expect, it } from "vitest";
import {
  bookingPreferenceHref,
  bookingSlotHref,
  readBookingPreference,
  readBookingSlotQuery,
} from "./bookingSlotLink";

// Reads the query of a built link back as `searchParams` would see it on the server.
const paramsOf = (href: string) =>
  Object.fromEntries(new URL(href, "http://otcms.local").searchParams);

describe("bookingSlotHref", () => {
  it("ghi dịch vụ, bác sĩ, ngày và giờ vào đường dẫn đặt lịch", () => {
    expect(
      bookingSlotHref({
        serviceId: "svc-kham-tq",
        doctorId: "doc-1",
        date: "2026-11-19",
        time: "08:15",
      }),
    ).toBe(
      "/portal/appointments/new?serviceId=svc-kham-tq&doctorId=doc-1&date=2026-11-19&time=08%3A15",
    );
  });

  it("không có bác sĩ thì bỏ tham số doctorId", () => {
    const href = bookingSlotHref({
      serviceId: "svc-kham-tq",
      doctorId: null,
      date: "2026-11-19",
      time: "08:15",
    });
    expect(paramsOf(href)).not.toHaveProperty("doctorId");
  });
});

describe("readBookingSlotQuery", () => {
  it("đọc lại đúng khung giờ mà bookingSlotHref đã ghi", () => {
    const slot = {
      serviceId: "svc-kham-tq",
      doctorId: "doc-1",
      date: "2026-11-19",
      time: "08:15",
    };
    expect(readBookingSlotQuery(paramsOf(bookingSlotHref(slot)))).toEqual(slot);
  });

  it("bác sĩ sai định dạng bị bỏ, khung giờ vẫn được giữ", () => {
    expect(
      readBookingSlotQuery({
        serviceId: "svc-kham-tq",
        doctorId: "doc 1; drop",
        date: "2026-11-19",
        time: "08:15",
      }),
    ).toEqual({
      serviceId: "svc-kham-tq",
      doctorId: null,
      date: "2026-11-19",
      time: "08:15",
    });
  });

  it("thiếu hoặc sai dịch vụ, ngày hoặc giờ thì trả null", () => {
    const valid = {
      serviceId: "svc-kham-tq",
      date: "2026-11-19",
      time: "08:15",
    };
    expect(readBookingSlotQuery({ ...valid, serviceId: undefined })).toBeNull();
    expect(readBookingSlotQuery({ ...valid, serviceId: "bad id!" })).toBeNull();
    expect(readBookingSlotQuery({ ...valid, date: "2026-02-30" })).toBeNull();
    expect(readBookingSlotQuery({ ...valid, date: "19/11/2026" })).toBeNull();
    expect(readBookingSlotQuery({ ...valid, time: "24:00" })).toBeNull();
    expect(readBookingSlotQuery({ ...valid, time: "8:15" })).toBeNull();
  });

  it("tham số lặp lại lấy giá trị đầu tiên", () => {
    expect(
      readBookingSlotQuery({
        serviceId: ["svc-a", "svc-b"],
        date: "2026-11-19",
        time: "08:15",
      }),
    ).toMatchObject({ serviceId: "svc-a" });
  });
});

describe("homepage booking preference", () => {
  it("keeps the selected service and date across the sign-in link without selecting a time", () => {
    const params = { serviceId: "service-123", date: "2026-11-24" };
    const preference = readBookingPreference(params);
    expect(preference).toEqual({ ...params, doctorId: null, time: null });
    expect(bookingPreferenceHref(preference!)).toBe(
      "/portal/appointments/new?serviceId=service-123&date=2026-11-24",
    );
    expect(readBookingPreference({ ...params, time: "invalid" })).toBeNull();
    expect(readBookingPreference({ ...params, date: "2026-02-30" })).toBeNull();
  });
});
