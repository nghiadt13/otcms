import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PublicErrorState } from "./PublicErrorState";
import { formatAppMessage } from "@/lib/messages/appMessages";
describe("PublicErrorState", () => {
  it("nói rõ lỗi bằng câu MSG-04 và có nút Thử lại gọi lại việc tải", async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    render(<PublicErrorState retry={retry} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
