import { expect, it } from "vitest";
import { validateIndicators } from "./serviceFieldRules";
import { indicatorsToRequest } from "./serviceFormDraft";
const row = {
  name: " Hb ",
  unit: " g/dL ",
  refMale: { min: "13,5", max: "17" },
  refFemale: { min: "12", max: "15" },
};
it("requires a row and finite, strictly increasing reference ranges", () => {
  expect(validateIndicators([])).toHaveProperty("indicators", "MSG-66");
  expect(validateIndicators([row])).toEqual({});
  for (const min of ["17", "18", "Infinity", "", "1e999"]) {
    expect(
      Object.keys(validateIndicators([{ ...row, refMale: { min, max: "17" } }]))
        .length,
    ).toBeGreaterThan(0);
  }
});
it("sends nested decimal ranges with trimmed names and units", () => {
  expect(indicatorsToRequest([row])[0]).toEqual({
    name: "Hb",
    unit: "g/dL",
    refMale: { min: 13.5, max: 17 },
    refFemale: { min: 12, max: 15 },
  });
});
