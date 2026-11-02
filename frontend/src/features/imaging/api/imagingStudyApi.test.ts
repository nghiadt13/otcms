import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiError } from "@/lib/api/apiError";
import { getPatientStudy, listPatientStudies } from "./imagingStudyApi";
import {
  IMAGING_FORBIDDEN_STUDY_ID,
  IMAGING_LIST_ERROR_PATIENT_ID,
} from "./imagingStudyMockData";

// Mã hồ sơ trùng kho hồ sơ đã gắn (lib/patient-scope, bộ default).
const LAN_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

describe("listPatientStudies", () => {
  it("liệt kê study mới nhất trước, không có viewerUrl, kết luận ẩn ở lượt Chưa hoàn tất", async () => {
    const page = await listPatientStudies({});
    expect(page.items.map((item) => item.id)).toEqual([
      "std-an-xray-1",
      "std-lan-us-1",
    ]);
    expect(page.items.every((item) => !("viewerUrl" in item))).toBe(true);
    expect(page.items[0]).toMatchObject({
      visitStatus: "COMPLETED",
      modality: "XRAY",
      finding: "Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày.",
    });
    expect(page.items[1]).toMatchObject({
      visitStatus: "INCOMPLETE",
      finding: null,
    });
  });

  it("lọc theo một hồ sơ và phân trang", async () => {
    const onlyLan = await listPatientStudies({ patientId: LAN_ID });
    expect(onlyLan.items.map((item) => item.patient.fullName)).toEqual([
      "Nguyễn Thị Lan",
    ]);
    const paged = await listPatientStudies({ page: 1, size: 1 });
    expect([paged.totalItems, paged.totalPages, paged.items[0].id]).toEqual([
      2,
      2,
      "std-lan-us-1",
    ]);
  });

  it("mã lọc lỗi trả 500 MSG-04", async () => {
    const error = await failure(
      listPatientStudies({ patientId: IMAGING_LIST_ERROR_PATIENT_ID }),
    );
    expect([error.status, error.code]).toEqual([500, "MSG-04"]);
  });
});

describe("getPatientStudy", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_DEV_URL", "");
  });

  it("chưa có URL trình xem thì viewerUrl là null", async () => {
    const study = await getPatientStudy("std-an-xray-1");
    expect(study.viewerUrl).toBeNull();
    expect(study.finding).toContain("Hẹp khe khớp");
  });

  it("có URL trình xem (môi trường dev) thì dựng theo StudyInstanceUIDs", async () => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_DEV_URL", "http://127.0.0.1:3001/viewer");
    const study = await getPatientStudy("std-an-xray-1");
    expect(study.viewerUrl).toBe(
      "http://127.0.0.1:3001/viewer?StudyInstanceUIDs=1.2.826.0.1.3680043.8.498.10230001",
    );
  });

  it("lượt Chưa hoàn tất không trả kết luận", async () => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_DEV_URL", "http://127.0.0.1:3001/viewer");
    const study = await getPatientStudy("std-lan-us-1");
    expect(study.visitStatus).toBe("INCOMPLETE");
    expect(study.finding).toBeNull();
  });

  it("trả đúng mã lỗi: 403 MSG-40, 404 MSG-41", async () => {
    const forbidden = await failure(
      getPatientStudy(IMAGING_FORBIDDEN_STUDY_ID),
    );
    expect([forbidden.status, forbidden.code]).toEqual([403, "MSG-40"]);
    const missing = await failure(getPatientStudy("std-khong-co"));
    expect([missing.status, missing.code]).toEqual([404, "MSG-41"]);
  });
});
