import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import {
  clearLocalAuthSession,
  getSession,
  type AuthSession,
} from "@/lib/auth/authSession";
import { AdminAccess } from "./AdminAccess";

const navigation = vi.hoisted(() => ({
  path: "/admin/reports",
  router: { replace: vi.fn() },
}));
vi.mock("next/navigation", () => ({
  usePathname: () => navigation.path,
  useRouter: () => navigation.router,
}));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  getSession: vi.fn(),
  clearLocalAuthSession: vi.fn(),
}));
const session = {
  accountType: "ADMIN",
  mustChangePassword: false,
} as AuthSession;
const account = { role: "ADMIN", status: "ACTIVE", mustChangePassword: false };
const view = () => (
  <AdminAccess>
    <p>Protected Admin reports</p>
  </AdminAccess>
);
beforeEach(() => {
  vi.clearAllMocks();
  navigation.path = "/admin/reports";
  vi.mocked(getSession).mockResolvedValue(session);
  vi.mocked(apiRequest).mockResolvedValue(account);
});

it("keeps children unmounted until Spring validates the session", async () => {
  let resolve!: (value: typeof account) => void;
  vi.mocked(apiRequest).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(view());
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
  await waitFor(() =>
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/me",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    ),
  );
  await act(async () => resolve(account));
  expect(screen.getByText("Protected Admin reports")).toBeInTheDocument();
});
it("redirects a signed-out direct visit without mounting reports or calling me", async () => {
  vi.mocked(getSession).mockResolvedValue(null);
  render(view());
  await waitFor(() =>
    expect(navigation.router.replace).toHaveBeenCalledWith("/staff-login"),
  );
  expect(apiRequest).not.toHaveBeenCalled();
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
});
it.each([
  "OWNER",
  "PATIENT",
  "DOCTOR",
  "RECEPTIONIST",
  "IMAGING_TECHNICIAN",
  "LAB_TECHNICIAN",
] as const)("denies %s", async (accountType) => {
  vi.mocked(getSession).mockResolvedValue({ ...session, accountType });
  render(view());
  await screen.findByRole("alert");
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
});
it.each([
  { ...account, role: "OWNER" },
  { ...account, status: "INACTIVE" },
])(
  "does not trust a cached Admin role over the live account %j",
  async (live) => {
    vi.mocked(apiRequest).mockResolvedValue(live);
    render(view());
    await screen.findByRole("alert");
    expect(
      screen.queryByText("Protected Admin reports"),
    ).not.toBeInTheDocument();
  },
);
it("redirects a revoked session rejected by Spring", async () => {
  vi.mocked(apiRequest).mockRejectedValue(new ApiError(401));
  render(view());
  await waitFor(() =>
    expect(navigation.router.replace).toHaveBeenCalledWith("/staff-login"),
  );
  expect(clearLocalAuthSession).toHaveBeenCalled();
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
});
it.each(["session", "account"])(
  "requires initial password change from %s",
  async (source) => {
    if (source === "session")
      vi.mocked(getSession).mockResolvedValue({
        ...session,
        mustChangePassword: true,
      });
    else
      vi.mocked(apiRequest).mockResolvedValue({
        ...account,
        mustChangePassword: true,
      });
    render(view());
    await waitFor(() =>
      expect(navigation.router.replace).toHaveBeenCalledWith(
        "/first-login-password",
      ),
    );
    expect(
      screen.queryByText("Protected Admin reports"),
    ).not.toBeInTheDocument();
  },
);
it("fails closed on network errors and supports retry", async () => {
  vi.mocked(apiRequest).mockRejectedValueOnce(new Error("offline"));
  render(view());
  await screen.findByRole("alert");
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(
    await screen.findByText("Protected Admin reports"),
  ).toBeInTheDocument();
});
it("rechecks an internal destination after logout, hiding previously allowed content", async () => {
  const { rerender } = render(view());
  await screen.findByText("Protected Admin reports");
  vi.mocked(getSession).mockResolvedValue(null);
  navigation.path = "/admin/accounts";
  rerender(view());
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
  await waitFor(() =>
    expect(navigation.router.replace).toHaveBeenCalledWith("/staff-login"),
  );
});
it("rechecks restored pages and a logout from another tab", async () => {
  render(view());
  await screen.findByText("Protected Admin reports");
  fireEvent(window, new Event("pageshow"));
  await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2));
  vi.mocked(getSession).mockResolvedValue(null);
  fireEvent(
    window,
    new StorageEvent("storage", {
      key: "otcms.auth.hasSession",
      newValue: null,
    }),
  );
  await waitFor(() =>
    expect(navigation.router.replace).toHaveBeenCalledWith("/staff-login"),
  );
  expect(clearLocalAuthSession).toHaveBeenCalled();
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
});

it("does not discard form input while revalidating on window focus", async () => {
  const { container } = render(
    <AdminAccess>
      <input aria-label="Draft" defaultValue="unsaved" />
    </AdminAccess>,
  );
  const input = await screen.findByLabelText("Draft");
  vi.mocked(apiRequest).mockReturnValue(new Promise(() => {}));
  fireEvent(window, new Event("focus"));
  await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2));
  expect(container.querySelector("input")).toBe(input);
  expect(input).toHaveValue("unsaved");
});

it("ignores an old successful check that finishes after another tab signs out", async () => {
  let resolve!: (value: typeof account) => void;
  vi.mocked(apiRequest).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(view());
  await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
  vi.mocked(getSession).mockResolvedValue(null);
  fireEvent(
    window,
    new StorageEvent("storage", {
      key: "otcms.auth.hasSession",
      newValue: null,
    }),
  );
  await waitFor(() =>
    expect(navigation.router.replace).toHaveBeenCalledWith("/staff-login"),
  );
  await act(async () => resolve(account));
  expect(screen.queryByText("Protected Admin reports")).not.toBeInTheDocument();
});
