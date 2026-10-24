import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it } from "vitest";
import { resetReceptionMockStore } from "../api/receptionMockStore";
import {
  forceNextReceptionQueueFailure,
  resetReceptionQueueMockControls,
  setReceptionQueueForcedEmpty,
} from "../api/receptionQueueMockControls";
import { addTestEntry } from "../api/receptionQueueTestEntries";
import { ReceptionQueueBoard } from "./ReceptionQueueBoard";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Kho giả mặc định: Phòng khám 1 có Kiều Thị Hạnh (#4), Châu Văn Nam (#8) đang chờ, Phùng Thị Mai (#6)
// vắng mặt. Phòng khám 2 có Tôn Thất Long (#5), Đặng Thị Xuân (#7), Ung Văn Quang (#11) đang chờ, Hà Văn
// Hải (#3) đang phục vụ. Màn chỉ đọc: không có nút thao tác hàng đợi.

const rowName = (name: string) => screen.findByRole("button", { name });

function renderBoard() {
  return render(
    <>
      <ReceptionQueueBoard />
      <Toaster />
    </>,
  );
}

beforeEach(() => {
  resetReceptionMockStore();
  resetReceptionQueueMockControls();
});

describe("ReceptionQueueBoard", () => {
  it("có đúng một tiêu đề cấp 1 và không có nút thao tác hàng đợi", async () => {
    renderBoard();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    await rowName("Tôn Thất Long");
    expect(screen.queryByRole("button", { name: /gọi|đánh dấu|đổi|chuyển/i })).toBeNull();
  });

  it("mặc định hiện Đang chờ và Vắng mặt theo thứ tự BE, kèm dòng tổng", async () => {
    renderBoard();
    await rowName("Tôn Thất Long");
    expect(screen.getAllByRole("row")).toHaveLength(7);
    expect(screen.queryByRole("button", { name: "Hà Văn Hải" })).toBeNull();
    // Đoạn mô tả đầu trang cũng có "đã tiếp nhận hôm nay", nên khớp thêm dấu "·" của dòng tổng.
    expect(screen.getByText(/hôm nay ·/, { selector: "p" })).toHaveTextContent(
      "9 đã tiếp nhận hôm nay · 5 đang chờ · 1 vắng mặt",
    );
  });

  it("chọn Tất cả thêm Đang phục vụ; chọn phòng chỉ hiện dòng của phòng đó", async () => {
    const user = userEvent.setup();
    renderBoard();
    await rowName("Tôn Thất Long");

    await user.selectOptions(screen.getByLabelText("Trạng thái"), "ALL");
    expect(await rowName("Hà Văn Hải")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Phòng"), "room-k1");
    await waitFor(() => expect(screen.queryByRole("button", { name: "Tôn Thất Long" })).toBeNull());
    expect(screen.getByRole("button", { name: "Kiều Thị Hạnh" })).toBeInTheDocument();
  });

  it("lọc tên hoặc số thứ tự ngay trên màn; không khớp thì có Xóa bộ lọc", async () => {
    const user = userEvent.setup();
    renderBoard();
    await rowName("Tôn Thất Long");
    const search = screen.getByLabelText("Lọc tên hoặc số thứ tự");

    await user.type(search, "long");
    expect(screen.getByRole("button", { name: "Tôn Thất Long" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đặng Thị Xuân" })).toBeNull();

    await user.clear(search);
    await user.type(search, "zzz");
    expect(screen.getByText("Không có bệnh nhân khớp bộ lọc.", { exact: false })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    expect(await rowName("Tôn Thất Long")).toBeInTheDocument();
  });

  it("hàng đợi trống theo dạy cách dùng và có link sang Tiếp nhận bệnh nhân", async () => {
    setReceptionQueueForcedEmpty(true);
    renderBoard();
    const table = await screen.findByRole("table");
    expect(within(table).getByText("Chưa có bệnh nhân nào trong hàng đợi.", { exact: false })).toBeInTheDocument();
    expect(within(table).getByRole("link", { name: "Tiếp nhận bệnh nhân" })).toHaveAttribute(
      "href",
      "/staff/check-in",
    );
  });

  it("lỗi 403 nói rõ là không có quyền và không có nút Thử lại", async () => {
    forceNextReceptionQueueFailure("queue", 403);
    renderBoard();
    expect(await screen.findByRole("alert")).toHaveTextContent("Bạn không có quyền xem hàng đợi tiếp nhận.");
    expect(screen.queryByRole("button", { name: "Thử lại" })).toBeNull();
  });

  it("lỗi 5xx có Thử lại và tải lại được", async () => {
    const user = userEvent.setup();
    forceNextReceptionQueueFailure("queue", 500);
    renderBoard();
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được hàng đợi.");
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await rowName("Tôn Thất Long")).toBeInTheDocument();
  });

  it("Tải lại lỗi khi đã có dữ liệu: giữ bảng cũ, báo toast MSG-04 và chữ chưa cập nhật", async () => {
    const user = userEvent.setup();
    renderBoard();
    await rowName("Tôn Thất Long");
    forceNextReceptionQueueFailure("queue", 500);
    await user.click(screen.getByRole("button", { name: "Tải lại" }));
    expect(await screen.findByText(formatAppMessage("MSG-04"))).toBeInTheDocument();
    expect(screen.getByText("Chưa cập nhật được, thử lại sau")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tôn Thất Long" })).toBeInTheDocument();
  });

  it("chọn dòng mở tóm tắt lượt khám: số, phòng, hàng đợi và trạng thái lượt khám", async () => {
    const user = userEvent.setup();
    renderBoard();
    await user.click(await rowName("Đặng Thị Xuân"));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("heading", { name: "Số 7 · Phòng khám 2" })).toBeInTheDocument();
    expect(within(dialog).getByText("Khám · Đang chờ · 1 người phía trước")).toBeInTheDocument();
    expect(within(dialog).getByText("Chờ khám")).toBeInTheDocument();
  });

  it("lượt đã rời hàng (404) báo toast MSG-38, đóng tóm tắt và tải lại bảng", async () => {
    const user = userEvent.setup();
    renderBoard();
    await rowName("Tôn Thất Long");
    // Bệnh nhân vừa tiếp nhận ở máy khác: chưa hiện cho tới khi bảng tải lại.
    addTestEntry({ patientName: "Người mới tiếp nhận", roomName: "Phòng khám 2", time: "09:50" });
    expect(screen.queryByRole("button", { name: "Người mới tiếp nhận" })).toBeNull();

    forceNextReceptionQueueFailure("summary", 404);
    await user.click(screen.getByRole("button", { name: "Tôn Thất Long" }));
    expect(
      await screen.findByText("Không thể xem danh sách chờ hoặc lượt khám này. Vui lòng tải lại và kiểm tra quyền truy cập."),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(await rowName("Người mới tiếp nhận")).toBeInTheDocument();
  });
});
