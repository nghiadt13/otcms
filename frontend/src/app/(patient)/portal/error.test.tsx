import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import PatientPortalError from "./error";

describe("PatientPortalError", () => {
  it("nói rõ chuyện gì xảy ra, hiện mã lỗi và gọi retry khi bấm Thử lại", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const retry = vi.fn();
    const error = Object.assign(new Error("boom"), { digest: "abc123" });

    render(<PatientPortalError error={error} retry={retry} />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Không mở được trang này",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Đã có lỗi khi tải trang.",
    );
    expect(screen.getByText("Mã lỗi: abc123")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute(
      "href",
      "/portal",
    );

    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
