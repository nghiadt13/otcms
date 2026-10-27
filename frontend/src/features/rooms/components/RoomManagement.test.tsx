import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as roomApi from "../api/roomApi";
import type { Room } from "../types/room";
import { RoomManagement } from "./RoomManagement";

// Giữ API mock thật; chỉ cho phép một test thay `listRooms` bằng fixture riêng.
vi.mock("../api/roomApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/roomApi")>();
  return { ...actual, listRooms: vi.fn(actual.listRooms) };
});

vi.setConfig({ testTimeout: 30000 });

const searchBox = () =>
  screen.getByRole("searchbox", { name: "Tìm phòng theo tên hoặc mã" });

async function search(text: string) {
  await userEvent.clear(searchBox());
  await userEvent.type(searchBox(), text);
}

describe("RoomManagement", () => {
  beforeEach(async () => {
    const actual =
      await vi.importActual<typeof import("../api/roomApi")>("../api/roomApi");
    actual.resetRoomMockStore();
    vi.mocked(roomApi.listRooms).mockImplementation(actual.listRooms);
  });

  it("tải danh sách, có phân trang, không có nút hay chữ Xóa", async () => {
    render(<RoomManagement />);
    expect(await screen.findByText("P101")).toBeInTheDocument();
    expect(screen.getByText(/23 bản ghi/)).toBeInTheDocument();
    expect(screen.queryByText(/xóa/i)).not.toBeInTheDocument();
  });

  it("trạng thái lọc không kết quả và lỗi tải có Thử lại", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");

    await search("khongco");
    expect(
      await screen.findByText(
        /Không có phòng nào khớp bộ lọc/,
        {},
        { timeout: 3000 },
      ),
    ).toBeInTheDocument();

    await search("loi-may-chu");
    expect(
      await screen.findByText(
        /Không tải được danh sách phòng/,
        {},
        { timeout: 3000 },
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("dialog tạo: dịch vụ khóa tới khi chọn loại, đổi loại bỏ lựa chọn, trùng tên báo dưới ô Tên", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");
    await userEvent.click(screen.getByRole("button", { name: /Thêm phòng/ }));

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("Chọn loại phòng để xem dịch vụ."),
    ).toBeInTheDocument();

    await userEvent.selectOptions(
      within(dialog).getByLabelText("Loại phòng"),
      "EXAMINATION",
    );
    const orthopedic = await within(dialog).findByRole("checkbox", {
      name: "Khám chỉnh hình",
    });
    await userEvent.click(orthopedic);
    expect(orthopedic).toBeChecked();

    await userEvent.selectOptions(
      within(dialog).getByLabelText("Loại phòng"),
      "IMAGING",
    );
    expect(
      within(dialog).queryByRole("checkbox", { name: "Khám chỉnh hình" }),
    ).not.toBeInTheDocument();
    await userEvent.selectOptions(
      within(dialog).getByLabelText("Loại phòng"),
      "EXAMINATION",
    );
    expect(
      await within(dialog).findByRole("checkbox", { name: "Khám chỉnh hình" }),
    ).not.toBeChecked();

    await userEvent.type(
      within(dialog).getByLabelText("Tên hoặc mã phòng"),
      "P101",
    );
    await userEvent.click(
      within(dialog).getByRole("checkbox", { name: "Tái khám" }),
    );
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Thêm phòng" }),
    );

    const alert = await within(dialog).findByText(/Tên đã được sử dụng/);
    expect(alert.closest("[role=alert]")).not.toBeNull();
  });

  it("dialog sửa: tên và loại chỉ đọc", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");
    await userEvent.click(
      screen.getByRole("button", { name: "Sửa phòng P101" }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Tên hoặc mã phòng/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/Loại phòng/)).toBeDisabled();
    expect(within(dialog).getByLabelText("Mô tả")).toBeEnabled();
    expect(
      within(dialog).getByRole("button", { name: "Lưu thay đổi" }),
    ).toBeInTheDocument();
  });

  it("Switch mở xác nhận và chỉ đổi sau khi xác nhận", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");
    const sw = screen.getByRole("switch", { name: /phòng P101/ });
    expect(sw).toBeChecked();

    await userEvent.click(sw);
    const confirm = await screen.findByRole("alertdialog");
    expect(
      within(confirm).getByText("Tạm ngưng phòng P101?"),
    ).toBeInTheDocument();
    expect(sw).toBeChecked();

    await userEvent.click(
      within(confirm).getByRole("button", { name: "Tạm ngưng" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("switch", { name: /phòng P101/ }),
      ).not.toBeChecked(),
    );
  });

  it("409 phòng đang dùng: Switch giữ giá trị cũ", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");
    await search("CĐHA-02");
    const sw = await screen.findByRole("switch", { name: /phòng CĐHA-02/ });

    await userEvent.click(sw);
    const confirm = await screen.findByRole("alertdialog");
    await userEvent.click(
      within(confirm).getByRole("button", { name: "Tạm ngưng" }),
    );

    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByRole("switch", { name: /phòng CĐHA-02/ }),
    ).toBeChecked();
  });

  it("Xem mở Sheet chi tiết", async () => {
    render(<RoomManagement />);
    await screen.findByText("P101");
    await userEvent.click(
      screen.getByRole("button", { name: "Xem phòng P101" }),
    );
    const sheet = await screen.findByRole("dialog");
    expect(
      await within(sheet).findByText("Phòng khám chỉnh hình, 3 giường khám."),
    ).toBeInTheDocument();
    expect(
      within(sheet).getByRole("button", { name: "Đánh dấu tạm ngưng" }),
    ).toBeInTheDocument();
  });

  it("ẩn Sửa và Switch khi thiếu allowedActions", async () => {
    const readOnly: Room = {
      id: "r1",
      name: "P900",
      type: "EXAMINATION",
      description: "",
      status: "AVAILABLE",
      supportedServices: [],
      allowedActions: [],
    };
    vi.mocked(roomApi.listRooms).mockResolvedValue({
      items: [readOnly],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    render(<RoomManagement />);
    expect(await screen.findByText("P900")).toBeInTheDocument();
    expect(screen.getByText("Chưa gán dịch vụ")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Sửa phòng/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Xem phòng P900" }),
    ).toBeInTheDocument();
  });
});
