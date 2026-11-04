import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { listConfirmedProfiles, listLinkedProfiles } from "./linkedProfilesApi";
import {
  LINKED_PROFILE_VARIANTS,
  replaceLinkedProfilesMock,
  resetLinkedProfilesMock,
  subscribeLinkedProfiles,
} from "./linkedProfilesMockStore";
import type { LinkedProfile } from "./types";

beforeEach(() => {
  resetLinkedProfilesMock();
});

describe("listLinkedProfiles", () => {
  it("trả hồ sơ, bản thân đứng đầu rồi theo tên", async () => {
    const { items } = await listLinkedProfiles();
    expect(items.map((item) => item.fullName)).toEqual([
      "Nguyễn Văn An",
      "Nguyễn Gia Huy",
      "Nguyễn Thị Lan",
    ]);
    expect(items[0].relationship).toBe("SELF");
  });

  it("kho rỗng trả danh sách rỗng", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    await expect(listLinkedProfiles()).resolves.toEqual({ items: [] });
  });

  it("kho lỗi ném ApiError 500 với MSG-04", async () => {
    resetLinkedProfilesMock({ failList: true });
    const error = await listLinkedProfiles().catch((err: unknown) => err);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500, code: "MSG-04" });
  });

  it("thay kho bằng replaceLinkedProfilesMock thì lần tải sau thấy ngay và báo cho người đăng ký", async () => {
    const next: LinkedProfile[] = [LINKED_PROFILE_VARIANTS.selfOnly[0]];
    let notified = 0;
    const unsubscribe = subscribeLinkedProfiles(() => {
      notified += 1;
    });

    replaceLinkedProfilesMock(next);
    expect(notified).toBe(1);
    const { items } = await listLinkedProfiles();
    expect(items).toHaveLength(1);

    unsubscribe();
    replaceLinkedProfilesMock(LINKED_PROFILE_VARIANTS.default);
    expect(notified).toBe(1);
  });
});

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

it("lấy hồ sơ thật và không đưa liên kết đang chờ vào bộ chọn", async () => {
  const profile = {
    fullName: "Hồ sơ thử",
    dateOfBirth: "2017-05-01",
    gender: "OTHER",
    relationship: "CHILD",
  };
  vi.mocked(apiRequest).mockResolvedValueOnce({
    items: [
      { ...profile, patientProfileId: "otp", linkStatus: "PENDING_OTP" },
      {
        ...profile,
        patientProfileId: "counter",
        linkStatus: "PENDING_COUNTER",
      },
      { ...profile, patientProfileId: "confirmed", linkStatus: "CONFIRMED" },
    ],
  });
  const signal = new AbortController().signal;
  const { items } = await listConfirmedProfiles(signal);
  expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/patient-profiles", {
    signal,
  });
  expect(items).toHaveLength(1);
  expect(items[0]).toMatchObject({ id: "confirmed", sex: "OTHER" });
});
