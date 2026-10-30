import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { getSession, type AuthSession } from "@/lib/auth/authSession";
import { OwnerAccess } from "./OwnerAccess";
vi.mock("@/lib/auth/authSession", () => ({ getSession: vi.fn() }));
it.each(["ADMIN", "PATIENT", null])(
  "blocks %s from mounting owner content",
  async (role) => {
    vi.mocked(getSession).mockResolvedValue(
      role ? ({ accountType: role } as AuthSession) : null,
    );
    render(
      <OwnerAccess>
        <p>Owner secret content</p>
      </OwnerAccess>,
    );
    await screen.findByRole("alert");
    expect(screen.queryByText("Owner secret content")).not.toBeInTheDocument();
  },
);
it("allows OWNER", async () => {
  vi.mocked(getSession).mockResolvedValue({
    accountType: "OWNER",
  } as AuthSession);
  render(
    <OwnerAccess>
      <p>Owner content</p>
    </OwnerAccess>,
  );
  expect(await screen.findByText("Owner content")).toBeInTheDocument();
});
