import { afterEach, describe, expect, it, vi } from "vitest";
import { imagingViewerPath } from "./imagingViewerApi";

const STUDY_UID = "1.2.826.0.1.3680043.8.498.10230001";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("imagingViewerPath", () => {
  it("builds a same-origin OHIF URL using the DICOM Study UID", () => {
    expect(imagingViewerPath(STUDY_UID)).toBe(
      `/viewer?StudyInstanceUIDs=${STUDY_UID}`,
    );
  });

  it("allows a configured same-origin viewer route", () => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_VIEWER_PATH", "/imaging/viewer");
    expect(imagingViewerPath(STUDY_UID)).toBe(
      `/imaging/viewer?StudyInstanceUIDs=${STUDY_UID}`,
    );
  });

  it.each([
    "1.2.3/../../other-study",
    "1.a.3",
    `${"1".repeat(65)}.2`,
  ])("rejects invalid Study UID %s", (uid) => {
    expect(() => imagingViewerPath(uid)).toThrow("DICOM Study UID is invalid");
  });

  it.each([
    "https://viewer.example/viewer",
    "//viewer.example/viewer",
    "/viewer/../dicom-web",
    "/viewer?StudyInstanceUIDs=other",
    "/viewer\\..\\dicom-web",
  ])("rejects unsafe viewer route %s", (path) => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_VIEWER_PATH", path);
    expect(() => imagingViewerPath(STUDY_UID)).toThrow(
      "same-origin path",
    );
  });
});
