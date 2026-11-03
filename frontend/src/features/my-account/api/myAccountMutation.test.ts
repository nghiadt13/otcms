import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { updateMyAvatar, removeMyAvatar } from "./myAccountApi";
import {
  requestMyEmailChange,
  verifyMyEmailChange,
} from "./myAccountProfileApi";
import { MY_ACCOUNT_CHANGED, MY_ACCOUNT_ACCESS_LOST } from "./myAccountEvents";
import {
  ACCOUNT_ID,
  OTHER_ACCOUNT_ID,
  patientResponse,
} from "./myAccountTestFixtures";

const state = vi.hoisted(() => ({ owner: "" as string | null }));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => (state.owner ? { accountId: state.owner } : null),
}));
beforeEach(() => {
  vi.resetAllMocks();
  state.owner = ACCOUNT_ID;
});
const operations = [
  {
    label: "upload",
    run: (signal?: AbortSignal) =>
      updateMyAvatar(
        new File(["photo"], "a.png", { type: "image/png" }),
        signal,
      ),
    response: patientResponse(),
  },
  { label: "delete", run: removeMyAvatar, response: undefined },
  {
    label: "request OTP",
    run: (signal?: AbortSignal) =>
      requestMyEmailChange("moi@example.com", signal),
    response: { message: "MSG-06" },
  },
  {
    label: "verify OTP",
    run: (signal?: AbortSignal) => verifyMyEmailChange("123456", signal),
    response: patientResponse({ email: "moi@example.com" }),
  },
];
describe.each(operations)(
  "$label race/account isolation",
  ({ run, response }) => {
    it.each(["switch", "logout", "abort"] as const)(
      "response sau %s không phát account event",
      async (mode) => {
        let finish!: (value: unknown) => void;
        vi.mocked(apiRequest).mockReturnValue(
          new Promise((resolve) => {
            finish = resolve;
          }),
        );
        const controller = new AbortController();
        const changed = vi.fn();
        window.addEventListener(MY_ACCOUNT_CHANGED, changed);
        try {
          const pending = run(controller.signal);
          const assertion = expect(pending).rejects.toMatchObject({
            name: "AccountRequestSupersededError",
          });
          if (mode === "abort") controller.abort();
          else state.owner = mode === "logout" ? null : OTHER_ACCOUNT_ID;
          finish(response);
          await assertion;
          expect(changed).not.toHaveBeenCalled();
        } finally {
          window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
        }
      },
    );
    it("401 cũ không xóa state của tài khoản B", async () => {
      let reject!: (error: unknown) => void;
      vi.mocked(apiRequest).mockReturnValue(
        new Promise((_, no) => {
          reject = no;
        }),
      );
      const lost = vi.fn();
      window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      try {
        const pending = run();
        const assertion = expect(pending).rejects.toMatchObject({
          name: "AccountRequestSupersededError",
        });
        state.owner = OTHER_ACCOUNT_ID;
        reject(new ApiError(401));
        await assertion;
        expect(lost).not.toHaveBeenCalled();
      } finally {
        window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      }
    });
  },
);

it("phiên bị xóa trong request: phát access-lost để xóa account đang hiển thị", async () => {
  const lost = vi.fn();
  window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
  vi.mocked(apiRequest).mockImplementation(async () => {
    state.owner = null;
    throw new Error("Bạn cần đăng nhập để tiếp tục.");
  });
  try {
    await expect(requestMyEmailChange("moi@example.com")).rejects.toMatchObject(
      { name: "AccountRequestSupersededError" },
    );
    expect(lost).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
  }
});
