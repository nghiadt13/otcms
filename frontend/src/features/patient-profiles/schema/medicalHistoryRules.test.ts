import { describe, expect, it } from "vitest";
import {
  invalidHistoryFields,
  isHistoryTextTooLong,
  MEDICAL_HISTORY_SECTIONS,
  trimHistoryText,
} from "./medicalHistoryRules";

describe("medical history R3 validation", () => {
  it.each(MEDICAL_HISTORY_SECTIONS)(
    "kiểm tra $field sau trim, tối đa 2000 UTF-16 units",
    ({ field }) => {
      const texts = {
        conditions: "",
        allergies: "",
        procedures: "",
        injuries: "",
        orthopedicHistory: "",
        version: 0,
      };
      expect(
        invalidHistoryFields({
          ...texts,
          [field]: " \t" + "😀".repeat(1000) + "\n ",
        }),
      ).toEqual([]);
      expect(
        invalidHistoryFields({ ...texts, [field]: "😀".repeat(1000) + "a" }),
      ).toEqual([field]);
    },
  );

  it("trim giống Java, giữ Unicode whitespace, newline nội bộ và dấu tiếng Việt", () => {
    expect(trimHistoryText(" \t Dị ứng\nthuốc  \r\n")).toBe("Dị ứng\nthuốc");
    expect(isHistoryTextTooLong("x".repeat(2000) + "\u00a0")).toBe(true);
  });

  it.each([-1, 0.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    "từ chối version không hợp lệ %s",
    (version) => {
      expect(
        invalidHistoryFields({
          conditions: "",
          allergies: "",
          procedures: "",
          injuries: "",
          orthopedicHistory: "",
          version,
        }),
      ).toEqual(["version"]);
    },
  );
});
