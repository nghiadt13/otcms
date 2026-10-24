import { describe, expect, it } from "vitest";
import { STAFF_ROUTES } from "./staffRoutes";

describe("STAFF_ROUTES", () => {
  it("các màn Lễ tân có đúng đường dẫn dưới /staff", () => {
    expect(STAFF_ROUTES.home).toBe("/staff");
    expect(STAFF_ROUTES.checkIn).toBe("/staff/check-in");
    expect(STAFF_ROUTES.receptionQueue).toBe("/staff/reception-queue");
    expect(STAFF_ROUTES.appointments).toBe("/staff/appointments");
    expect(STAFF_ROUTES.newAppointment).toBe("/staff/appointments/new");
  });

  it("mọi đường dẫn đều nằm trong /staff và không trùng nhau", () => {
    const paths = Object.values(STAFF_ROUTES);
    expect(paths.every((path) => path.startsWith("/staff"))).toBe(true);
    expect(new Set(paths).size).toBe(paths.length);
  });
});
