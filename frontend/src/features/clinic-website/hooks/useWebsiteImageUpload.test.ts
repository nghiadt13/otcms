import { act, renderHook } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { useWebsiteImageUpload } from "./useWebsiteImageUpload";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
it("rejects empty, unsupported or oversized files before uploading", async () => {
  const { result } = renderHook(() => useWebsiteImageUpload());
  for (const file of [
    new File([], "empty.png", { type: "image/png" }),
    new File(["html"], "page.html", { type: "text/html" }),
    new File([new Uint8Array(2097153)], "large.png", { type: "image/png" }),
  ]) {
    await act(async () => {
      expect(await result.current.upload(file)).toBeNull();
    });
    expect(result.current.error).toContain("2 MB");
  }
  expect(request).not.toHaveBeenCalled();
});
