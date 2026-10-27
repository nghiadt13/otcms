import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { authMessages } from "../authMessages";
import { ForgotPasswordForm } from "./ForgotPasswordForm";

describe("ForgotPasswordForm", () => {
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
