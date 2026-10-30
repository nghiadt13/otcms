import { beforeEach, expect, it, vi } from "vitest";
import { saveDoctorDirectory, saveDoctorProfile } from "./adminDoctorApi";
import type { AdminDoctor } from "@/lib/domain/adminDoctor";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
const input = {
  fullName: "Doctor",
  photoUrl: null,
  workContact: "work@example.com",
  specialties: "Orthopedics",
  qualifications: "MD",
  biography: "",
  consultationInformation: "",
};
it("links an account only on creation, never when editing", async () => {
  await saveDoctorProfile(undefined, "account", input);
  expect(request).toHaveBeenLastCalledWith("/api/v1/admin/doctor-profiles", {
    method: "POST",
    body: JSON.stringify({ ...input, accountId: "account" }),
  });
  await saveDoctorProfile("doctor", "different-account", input);
  expect(JSON.parse(request.mock.calls[1][1].body)).not.toHaveProperty(
    "accountId",
  );
});
it("preserves unrelated directory flags when pausing new bookings", async () => {
  await saveDoctorDirectory(
    {
      doctorProfileId: "doctor",
      publiclyVisible: true,
      acceptingNewBookings: true,
      inActiveDirectory: true,
    } as AdminDoctor,
    { acceptingNewBookings: false },
  );
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
    publiclyVisible: true,
    acceptingNewBookings: false,
    inActiveDirectory: true,
  });
});
