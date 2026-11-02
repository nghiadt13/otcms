import { describe, expect, it } from "vitest";
import { createReceptionSeed } from "../api/receptionSeed";
import { matchesPatientSearch } from "./receptionRules";

const patients = createReceptionSeed("2026-10-27").patients;
describe("receptionist patient discovery", () => {
  it.each([
    "Bình",
    "binh",
    "  tRaN   bINH  ",
    "0900000002",
    "+84 900 000 002",
    "BN0002",
    "bn-0002",
  ])(
    "finds Binh by name, phone or patient code without requiring a birthdate: %s",
    (q) => {
      expect(
        patients.filter((p) => matchesPatientSearch(p, { q })).map((p) => p.id),
      ).toEqual(["BN0002"]);
    },
  );
  it("returns multiple candidates for partial phone numbers", () => {
    expect(
      patients.filter((p) => matchesPatientSearch(p, { q: "090000000" })),
    ).toHaveLength(9);
  });
  it("applies the optional birthdate in addition to the keyword", () => {
    expect(
      matchesPatientSearch(patients[1], { q: "Bình", dob: "1948-08-12" }),
    ).toBe(true);
    expect(
      matchesPatientSearch(patients[1], { q: "Bình", dob: "1990-01-01" }),
    ).toBe(false);
  });
  it.each(["", "   ", "---", "+++", "."])(
    "does not expose the whole list for an empty or punctuation-only query: %s",
    (q) => {
      expect(patients.filter((p) => matchesPatientSearch(p, { q }))).toEqual(
        [],
      );
    },
  );
});
