import { describe, expect, it } from "vitest";
import { PATIENT_ROUTES } from "./patientRoutes";

describe("PATIENT_ROUTES", () => {
  it("các màn danh sách và mục menu có đúng đường dẫn", () => {
    expect(PATIENT_ROUTES.appointments).toBe("/portal/appointments");
    expect(PATIENT_ROUTES.visits).toBe("/portal/visits");
    expect(PATIENT_ROUTES.results).toBe("/portal/results");
    expect(PATIENT_ROUTES.prescriptions).toBe("/portal/prescriptions");
    expect(PATIENT_ROUTES.images).toBe("/portal/images");
    expect(PATIENT_ROUTES.payments).toBe("/portal/payments");
    expect(PATIENT_ROUTES.paymentHistory).toBe("/portal/payments/history");
    expect(PATIENT_ROUTES.profiles).toBe("/portal/profiles");
    expect(PATIENT_ROUTES.account).toBe("/portal/account");
  });

  it("route có id tạo đúng URL và mã hóa ký tự đặc biệt", () => {
    expect(PATIENT_ROUTES.appointmentDetail("ap-1")).toBe(
      "/portal/appointments/ap-1",
    );
    expect(PATIENT_ROUTES.newAppointment).toBe("/portal/appointments/new");
    expect(PATIENT_ROUTES.visitDetail("v-1")).toBe("/portal/visits/v-1");
    expect(PATIENT_ROUTES.resultDetail("v-1")).toBe("/portal/results/v-1");
    expect(PATIENT_ROUTES.prescriptionDetail("rx-1")).toBe(
      "/portal/prescriptions/rx-1",
    );
    expect(PATIENT_ROUTES.imageStudy("st-1")).toBe("/portal/images/st-1");
    expect(PATIENT_ROUTES.payAppointment("ap-1")).toBe(
      "/portal/payments/appointment/ap-1",
    );
    expect(PATIENT_ROUTES.payVisit("v-1")).toBe("/portal/payments/visit/v-1");
    expect(PATIENT_ROUTES.paymentResult).toBe("/portal/payments/result");
    expect(PATIENT_ROUTES.paymentDetail("pm-1")).toBe(
      "/portal/payments/history/pm-1",
    );
    expect(PATIENT_ROUTES.profileDetail("pf-1")).toBe("/portal/profiles/pf-1");
    expect(PATIENT_ROUTES.newProfile).toBe("/portal/profiles/new");
    expect(PATIENT_ROUTES.notifications).toBe("/portal/notifications");
    expect(PATIENT_ROUTES.newReview("v-1")).toBe(
      "/portal/reviews/new?visitId=v-1",
    );
    expect(PATIENT_ROUTES.profileDetail("a/b?c")).toBe(
      "/portal/profiles/a%2Fb%3Fc",
    );
  });

  it("mọi đường dẫn nằm trong /portal", () => {
    for (const value of Object.values(PATIENT_ROUTES)) {
      const path = typeof value === "function" ? value("sample") : value;
      expect(path.startsWith("/portal")).toBe(true);
    }
  });
});
