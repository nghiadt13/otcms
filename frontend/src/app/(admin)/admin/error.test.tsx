import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AdminError from "./error";

describe("AdminError", () => {
  it("hiện mã lỗi (digest) và gọi retry khi bấm Thử lại", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const retry = vi.fn();
    const error = Object.assign(new Error("boom"), { digest: "abc123" });

    render(<AdminError error={error} retry={retry} />);

    expect(screen.getByText("Mã lỗi: abc123")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
