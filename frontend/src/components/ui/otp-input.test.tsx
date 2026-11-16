import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { OtpInput } from "./otp-input";

function Harness({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <label htmlFor="otp">Mã OTP</label>
      <OtpInput id="otp" onChange={setValue} value={value} />
    </>
  );
}

describe("OtpInput", () => {
  it("chỉ nhận chữ số, tối đa 6, và gắn được nhãn", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Mã OTP");
    await user.type(input, "1a2b34567");
    expect(input).toHaveValue("123456");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
  });

  it("dán cả mã vào một lần và xóa lùi từng số", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText("Mã OTP");
    await user.click(input);
    await user.paste("482 913");
    expect(input).toHaveValue("482913");
    await user.keyboard("{Backspace}");
    expect(input).toHaveValue("48291");
  });

  it("báo lỗi qua aria-invalid", () => {
    render(<OtpInput id="x" invalid onChange={() => {}} value="" />);
    expect(screen.getByRole("textbox")).toHaveAttribute("aria-invalid", "true");
  });
});
