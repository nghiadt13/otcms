import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { listLinkedProfiles } from "./linkedProfilesApi";
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
