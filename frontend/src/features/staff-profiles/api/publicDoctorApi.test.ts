import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listPublicDoctors,
  getPublicDoctor,
  listFeaturedDoctors,
} from "./publicDoctorApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
const doctor = {
  doctorProfileId: "d",
  fullName: "Doctor from BE",
  photoUrl: null,
  specialties: "Orthopedics",
  qualifications: "MD",
  biography: "Biography",
  consultationInformation: "Consultation",
};
it("maps actual public BE data for directory and homepage without reading a mock store", async () => {
  request.mockResolvedValue({ items: [doctor] });
  expect((await listPublicDoctors()).items[0]).toMatchObject({
    id: "d",
    specialties: ["Orthopedics"],
    consultationInfo: "Consultation",
  });
  expect(request.mock.calls[0][0]).toBe("/api/v1/doctors");
  expect(await listFeaturedDoctors(1)).toHaveLength(1);
  request.mockResolvedValue({ items: [] });
  expect(await listFeaturedDoctors()).toEqual([]);
});
it("uses the seven-day availability returned by the doctor detail endpoint", async () => {
  const availability = [
    { date: "2026-11-01", hasOpenTime: false },
    { date: "2026-11-02", hasOpenTime: true },
  ];
  request.mockResolvedValue({ ...doctor, availability });
  expect((await getPublicDoctor("d")).availability).toEqual(availability);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0][0]).toBe("/api/v1/doctors/d");
});
it("maps hidden doctor 404 to MSG-24 and propagates outages", async () => {
  request.mockRejectedValue(new ApiError(404));
  await expect(getPublicDoctor("hidden")).rejects.toMatchObject({
    code: "MSG-24",
  });
  request.mockRejectedValue(new ApiError(503));
  expect(
    await listPublicDoctors().catch((e) => ({ status: e.status })),
  ).toEqual({ status: 503 });
});
