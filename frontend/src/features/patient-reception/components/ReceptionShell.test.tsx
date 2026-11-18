import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ReceptionShell } from "./ReceptionShell";

const mocks = vi.hoisted(() => ({ path: "/reception/payments", account: vi.fn() }));
vi.mock("next/navigation", () => ({ usePathname: () => mocks.path, useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/auth/currentAccountApi", () => ({ fetchCurrentAccount: mocks.account }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, Toaster: () => null }));

beforeEach(() => {
  mocks.path = "/reception/payments";
  mocks.account.mockResolvedValue({ username: "reception.one", displayName: "Reception", avatarUrl: null });
});

it("keeps the demo banner on a demo-only screen when enabled", () => {
  mocks.path = "/reception/notifications";
  vi.stubEnv("NODE_ENV", "production");
  vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "true");
  try {
    render(<ReceptionShell><p>Demo screen</p></ReceptionShell>);
    expect(screen.getByText(/Bản xem thử giao diện · dữ liệu mẫu/)).toBeInTheDocument();
  } finally { vi.unstubAllEnvs(); }
});

it("shows the signed-in account and no demo banner on a live screen", async () => {
  vi.stubEnv("NODE_ENV", "production");
  vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "");
  try {
    render(<ReceptionShell><p>Live screen</p></ReceptionShell>);
    expect(await screen.findByText("reception.one")).toBeInTheDocument();
    expect(screen.queryByText("thienquang")).not.toBeInTheDocument();
    expect(screen.queryByText(/Bản xem thử giao diện · dữ liệu mẫu/)).not.toBeInTheDocument();
  } finally { vi.unstubAllEnvs(); }
});
