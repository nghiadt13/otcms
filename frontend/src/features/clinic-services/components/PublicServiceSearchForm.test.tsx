import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PublicServiceSearchForm } from "./PublicServiceSearchForm";
import { formatAppMessage } from "@/lib/messages/appMessages";
describe("PublicServiceSearchForm", () => {
  it("điền lại tiêu chí đang lọc và gửi GET tới /services", () => {
    render(
      <PublicServiceSearchForm
        error={null}
        input={{ q: "gối", type: "EXAMINATION" }}
      />,
    );
    expect(screen.getByRole("textbox", { name: "Tên dịch vụ" })).toHaveValue(
      "gối",
    );
    expect(screen.getByRole("combobox", { name: "Loại dịch vụ" })).toHaveValue(
      "EXAMINATION",
    );
    const form = screen.getByRole("search");
    expect(form).toHaveAttribute("action", "/services");
    expect(form).toHaveAttribute("method", "get");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("tiêu chí sai hiện MSG-54 bằng chữ, đánh dấu ô tên", () => {
    render(
      <PublicServiceSearchForm
        error={{ field: "q", code: "MSG-54" }}
        input={{ q: "a".repeat(101), type: "" }}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-54"),
    );
    expect(
      screen.getByRole("textbox", { name: "Tên dịch vụ" }),
    ).toHaveAttribute("aria-invalid", "true");
  });
});
