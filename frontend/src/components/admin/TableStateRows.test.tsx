import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Table, TableBody } from "@/components/ui/table";
import { TableStateRows } from "./TableStateRows";

function setup(props: Partial<React.ComponentProps<typeof TableStateRows>>) {
  return render(
    <Table>
      <TableBody>
        <TableStateRows
          colSpan={3}
          loading={false}
          error={null}
          empty={false}
          emptyText="Chưa có phòng nào."
          {...props}
        />
      </TableBody>
    </Table>,
  );
}

describe("TableStateRows", () => {
  it("vẽ skeleton khi đang tải", () => {
    const { container } = setup({ loading: true, skeletonRows: 3 });
    expect(container.querySelectorAll("tr")).toHaveLength(3);
  });

  it("hiện lỗi và gọi onRetry", async () => {
    const onRetry = vi.fn();
    setup({ error: "Lỗi tải", onRetry });
    expect(screen.getByRole("alert")).toHaveTextContent("Lỗi tải");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(onRetry).toHaveBeenCalled();
  });

  it("ẩn Thử lại khi không có onRetry", () => {
    setup({ error: "Không đủ quyền" });
    expect(screen.queryByRole("button", { name: "Thử lại" })).toBeNull();
  });

  it("hiện chữ trống", () => {
    setup({ empty: true });
    expect(screen.getByText("Chưa có phòng nào.")).toBeInTheDocument();
  });

  it("không vẽ gì khi có dữ liệu", () => {
    const { container } = setup({});
    expect(container.querySelectorAll("tr")).toHaveLength(0);
  });
});
