import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LaboratoryAuthGate } from "./LaboratoryAuthGate";

const { replace, getSession } = vi.hoisted(() => ({
  replace: vi.fn(),
  getSession: vi.fn(),
}));
const router = { replace };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/authSession", () => ({ getSession }));

beforeEach(() => {
  replace.mockClear();
  getSession.mockReset();
});
describe("LaboratoryAuthGate", () => {
  it("allows the laboratory technician to access the page", async () => {
    getSession.mockResolvedValue({
      accountType: "LAB_TECHNICIAN",
      mustChangePassword: false,
    });
    render(
      <LaboratoryAuthGate>
        <p>Laboratory content</p>
      </LaboratoryAuthGate>,
    );
    expect(await screen.findByText("Laboratory content")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
  it.each([
    [null, "/staff-login"],
    [{ accountType: "DOCTOR", mustChangePassword: false }, "/workspace"],
    [
      { accountType: "LAB_TECHNICIAN", mustChangePassword: true },
      "/first-login-password",
    ],
  ])(
    "redirects and hides content when session is %j",
    async (session, destination) => {
      getSession.mockResolvedValue(session);
      render(
        <LaboratoryAuthGate>
          <p>Laboratory content</p>
        </LaboratoryAuthGate>,
      );
      await waitFor(() => expect(replace).toHaveBeenCalledWith(destination));
      expect(screen.queryByText("Laboratory content")).not.toBeInTheDocument();
    },
  );
  it("allows retry when the session request fails", async () => {
    getSession
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({
        accountType: "LAB_TECHNICIAN",
        mustChangePassword: false,
      });
    render(
      <LaboratoryAuthGate>
        <p>Laboratory content</p>
      </LaboratoryAuthGate>,
    );
    const retry = await screen.findByRole("button", { name: "Thử lại" });
    retry.click();
    expect(await screen.findByText("Laboratory content")).toBeInTheDocument();
  });
});
