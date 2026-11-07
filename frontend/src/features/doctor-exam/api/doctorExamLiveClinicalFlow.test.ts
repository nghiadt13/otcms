import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { doctorLiveApi as api } from "./doctorExamLiveApi";
import type { Prescription } from "../types/doctorExam";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
const line = {
  medicine: "Medicine",
  dose: "1 tablet",
  frequency: "Twice daily",
  usage: "After meals",
};
const form: Prescription = {
  id: null,
  version: 0,
  status: "DRAFT",
  issuedAt: null,
  lines: [line],
};
const dto = (version = 1) => ({
  id: "rx",
  visitId: "current",
  patientProfileId: "patient",
  version,
  status: "DRAFT",
  issuedAt: null,
  items: [{ lineNumber: 1, ...line }],
});
describe("UC36", () => {
  it("GET maps canonical identity/version/items", async () => {
    request.mockResolvedValue(dto(7));
    expect(await api.getPrescription("current")).toEqual({
      ...form,
      id: "rx",
      version: 7,
    });
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/current/prescription",
      { signal: undefined },
    );
  });
  it("absent MSG-41 is null; authorization failures propagate", async () => {
    request.mockRejectedValue(new ApiError(404, { code: "MSG-41" }));
    expect(await api.getPrescription("current")).toBeNull();
    const denied = new ApiError(403);
    request.mockRejectedValue(denied);
    await expect(api.getPrescription("current")).rejects.toBe(denied);
  });
  it.each([0, 7])(
    "saves canonical items using actual version %i",
    async (version) => {
      request.mockResolvedValue(dto(version + 1));
      await api.savePrescription(
        "current",
        { ...form, id: version ? "rx" : null, version },
        false,
      );
      expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
        version,
        items: [line],
      });
      expect(request.mock.calls[0][1].method).toBe("PUT");
    },
  );
  it("saves first and issues returned version with an idempotency key", async () => {
    request.mockResolvedValueOnce(dto(8)).mockResolvedValueOnce({
      ...dto(9),
      status: "ISSUED",
      issuedAt: "2026-11-06T00:00:00Z",
    });
    expect(
      await api.savePrescription(
        "current",
        { ...form, id: "rx", version: 7 },
        true,
      ),
    ).toMatchObject({ status: "ISSUED", version: 9 });
    expect(request.mock.calls[1][0]).toBe(
      "/api/v1/visits/current/prescription/issuance",
    );
    expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({ version: 8 });
    expect(request.mock.calls[1][1].headers["Idempotency-Key"]).toMatch(
      /^[0-9a-f-]{36}$/,
    );
  });
  it("stale 409 propagates without issuance", async () => {
    const stale = new ApiError(409);
    request.mockRejectedValue(stale);
    await expect(api.savePrescription("current", form, true)).rejects.toBe(
      stale,
    );
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("never retries ambiguous issuance", async () => {
    const error = new TypeError("Network unavailable");
    request.mockResolvedValueOnce(dto()).mockRejectedValueOnce(error);
    await expect(api.savePrescription("current", form, true)).rejects.toBe(
      error,
    );
    expect(request).toHaveBeenCalledTimes(2);
  });
  it("rejects partial rows and issued edits, ignores wholly blank extras", async () => {
    await expect(
      api.savePrescription(
        "current",
        { ...form, lines: [{ ...line, medicine: "" }] },
        false,
      ),
    ).rejects.toMatchObject({ status: 400 });
    await expect(
      api.savePrescription("current", { ...form, status: "ISSUED" }, true),
    ).rejects.toMatchObject({ status: 409 });
    expect(request).not.toHaveBeenCalled();
    request.mockResolvedValue(dto());
    await api.savePrescription(
      "current",
      {
        ...form,
        lines: [line, { medicine: "", dose: "", frequency: "", usage: "" }],
      },
      false,
    );
    expect(JSON.parse(request.mock.calls[0][1].body).items).toEqual([line]);
  });
});
const old = {
  visitId: "old",
  arrivedAt: "2026-08-01T01:00:00Z",
  examination: {
    diagnoses: [
      { code: "S", type: "SECONDARY" },
      { code: "M54.5", type: "PRIMARY" },
    ],
  },
  advice: {
    homeCareAdvice: " Rest ",
    activityAdvice: "",
    warningSigns: " Fever ",
  },
  prescription: { id: "issued", issuedAt: "2026-08-01T02:00:00Z" },
  documents: [],
  imagingResults: [{ imagingType: "MRI", findingsText: "Released finding" }],
};
const page = (items: unknown[] = [old]) => ({
  currentVisitId: "current",
  patientProfileId: "patient",
  items,
  page: 0,
  size: 20,
  total: items.length,
});
describe("UC33", () => {
  it("uses current Visit and maps primary diagnosis, advice, issued Rx and released imaging; no lab", async () => {
    request.mockResolvedValue(page());
    expect(await api.listPastVisits("current")).toEqual([
      {
        visitId: "old",
        visitDate: old.arrivedAt,
        status: "COMPLETED",
        primaryDiagnosis: "M54.5",
        advice: "Rest · Fever",
        prescriptions: 1,
        imaging: ["MRI: Released finding"],
        laboratory: [],
      },
    ]);
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/current/past-medical-records",
      { signal: undefined },
    );
  });
  it("missing optional data stays null/empty", async () => {
    request.mockResolvedValue(
      page([
        {
          ...old,
          examination: null,
          advice: null,
          prescription: null,
          imagingResults: [],
        },
      ]),
    );
    expect((await api.listPastVisits("current"))[0]).toMatchObject({
      primaryDiagnosis: null,
      advice: null,
      prescriptions: 0,
      imaging: [],
      laboratory: [],
    });
  });
  it("empty records and errors remain distinct", async () => {
    request.mockResolvedValue(page([]));
    expect(await api.listPastVisits("current")).toEqual([]);
    const error = new ApiError(503);
    request.mockRejectedValue(error);
    await expect(api.listPastVisits("current")).rejects.toBe(error);
  });
  it("all pages retain current Visit scope", async () => {
    request
      .mockResolvedValueOnce({ ...page(), size: 1, total: 2 })
      .mockResolvedValueOnce({
        ...page([{ ...old, visitId: "older" }]),
        page: 1,
        size: 1,
        total: 2,
      });
    expect(await api.listPastVisits("current")).toHaveLength(2);
    expect(request.mock.calls[1][0]).toBe(
      "/api/v1/visits/current/past-medical-records?page=1&size=1",
    );
  });
  it("capabilities stay honest", () => {
    expect(api.capabilities).toMatchObject({
      prescriptions: true,
      pastRecords: true,
      laboratoryOrders: false,
      orderListing: true,
      resultSheet: false,
    });
    expect(api).not.toHaveProperty("completeVisit");
    expect(api.capabilities).not.toHaveProperty("closeWithEvidence");
  });
});
