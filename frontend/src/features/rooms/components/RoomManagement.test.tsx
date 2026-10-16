import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { RoomManagement } from "./RoomManagement";

// Dùng API giả thật (độ trễ ~250ms) nên chờ bằng findBy*; không mock hook để test
// đi qua đủ lớp component → hook → api, như cách màn hình chạy thật.
describe("RoomManagement", () => {
  it("tải danh sách phòng mẫu rồi lọc theo tên", async () => {
    render(<RoomManagement />);

    expect(await screen.findByText("Phòng X-quang")).toBeInTheDocument();
    expect(screen.getByText("Phòng khám 101")).toBeInTheDocument();

    await userEvent.type(
      screen.getByRole("textbox", { name: "Tìm theo tên phòng" }),
      "x-quang",
    );

    expect(screen.getByText("Phòng X-quang")).toBeInTheDocument();
    expect(screen.queryByText("Phòng khám 101")).not.toBeInTheDocument();
  });
});
