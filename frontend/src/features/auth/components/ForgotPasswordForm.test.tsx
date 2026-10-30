import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetAuthSessionForTest } from "@/lib/auth/authSession";
import { authMessages } from "../authMessages";
import { ForgotPasswordForm } from "./ForgotPasswordForm";

const fetchMock = vi.fn();
beforeEach(() => {
  resetAuthSessionForTest();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockImplementation((url: string) =>
    Promise.resolve(
      url.endsWith("/csrf")
        ? new Response(JSON.stringify({ csrfToken: "csrf-test" }))
        : new Response("If the account is eligible...", { status: 202 }),
    ),
  );
});
afterEach(() => vi.unstubAllGlobals());

describe("ForgotPasswordForm", () => {
  it("does not show a sent confirmation when the backend cannot be reached", async () => {
    fetchMock.mockRejectedValue(new Error("offline"));
    render(<ForgotPasswordForm />);
    await userEvent.type(screen.getByLabelText(/Email/), "ai@example.com");
    await userEvent.click(
      screen.getByRole("button", { name: "Gửi hướng dẫn" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể hoàn tất yêu cầu",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
  it("báo MSG-08 khi email sai định dạng", async () => {
    render(<ForgotPasswordForm />);
    await userEvent.type(screen.getByLabelText(/Email/), "sai-email");
    await userEvent.click(
      screen.getByRole("button", { name: "Gửi hướng dẫn" }),
    );

    expect(screen.getByRole("alert")).toHaveTextContent(authMessages["MSG-08"]);
  });

  it("luôn hiện MSG-02 sau khi gửi, không lộ email có tồn tại hay không", async () => {
    render(<ForgotPasswordForm />);
    await userEvent.type(screen.getByLabelText(/Email/), "ai@example.com");
    await userEvent.click(
      screen.getByRole("button", { name: "Gửi hướng dẫn" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      authMessages["MSG-02"],
    );
    expect(screen.getByText("ai@example.com")).toBeInTheDocument();
  });
});
