import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  appointmentTarget,
  checkInButton,
  patientTarget,
  registerPanelTestLifecycle,
  renderPanel,
  tickPresent,
} from "./checkInPanelTestUtils";
import { formatAppMessage } from "@/lib/messages/appMessages";
registerPanelTestLifecycle();

describe("CheckInPanel: lịch hẹn và phí", () => {
  it("lịch của Nguyễn Văn An: của ai, lựa chọn mặc định là lịch hẹn, còn thiếu phí và có mặt", async () => {
    renderPanel(appointmentTarget("apt-1001"));
    expect(await screen.findByRole("heading", { level: 2, name: "Nguyễn Văn An" })).toBeInTheDocument();
    expect(screen.getByText("BN-000123")).toBeInTheDocument();
    expect(screen.queryByText(/tiền sử|dị ứng/i)).toBeNull();
    expect(screen.getByRole("radio", { name: /Lịch hẹn 08:30/ })).toBeChecked();
    expect(screen.getByText("Chưa thanh toán")).toBeInTheDocument();
    expect(screen.getByText("Còn thiếu: thu phí khám, xác nhận có mặt")).toBeInTheDocument();
    expect(checkInButton()).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "Nhóm ưu tiên" })).toHaveValue("");
  });

  it("thu tiền mặt: báo MSG-48, phí thành đã thanh toán; có mặt rồi tiếp nhận thì hiện phiếu số 12", async () => {
    const user = userEvent.setup();
    const handlers = renderPanel(appointmentTarget("apt-1001"));
    await screen.findByRole("heading", { level: 2, name: "Nguyễn Văn An" });

    await user.click(screen.getByRole("button", { name: /Đã nhận đủ 200.000/ }));
    expect(
      await screen.findByText(formatAppMessage("MSG-48", { balance: "200.000 ₫" })),
    ).toBeInTheDocument();
    expect(screen.getByText("Đã thanh toán")).toBeInTheDocument();
    expect(screen.getByText(/Phiếu thu PT-/)).toBeInTheDocument();
    expect(screen.getByText("Còn thiếu: xác nhận có mặt")).toBeInTheDocument();

    await tickPresent(user);
    expect(checkInButton()).toBeEnabled();
    await user.click(checkInButton()!);

    const ticket = await screen.findByRole("heading", { level: 2, name: "Đã tiếp nhận Nguyễn Văn An" });
    expect(ticket).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("12");
    expect(screen.getByText("Phòng khám 2 · BS. Phạm Thu Hà")).toBeInTheDocument();
    expect(screen.getByText("Hàng đợi khám · Có 3 người phía trước")).toBeInTheDocument();
    expect(
      screen.getByText("Đã tiếp nhận bệnh nhân. Số thứ tự 12, phòng khám 2."),
    ).toBeInTheDocument();
    expect(handlers.onCheckedIn).toHaveBeenCalledTimes(1);
  });

  it("thu tiền mặt bị 409 MSG-49 (đã trả nơi khác): báo lỗi và tải lại, phí thành đã thanh toán", async () => {
    const user = userEvent.setup();
    renderPanel(appointmentTarget("apt-race"));
    await screen.findByRole("heading", { level: 2, name: "Ngô Thị Nga" });

    await user.click(screen.getByRole("button", { name: /Đã nhận đủ 200.000/ }));
    expect(
      await screen.findByText(formatAppMessage("MSG-49")),
    ).toBeInTheDocument();
    expect(await screen.findByText("Đã thanh toán")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Đã nhận đủ/ })).toBeNull();
  });

  it("tiếp nhận bị 409 MSG-37 (lễ tân khác đã tiếp nhận): báo lỗi, tải lại và chỉ còn thông báo đã tiếp nhận", async () => {
    const user = userEvent.setup();
    renderPanel(appointmentTarget("apt-1009"));
    await screen.findByRole("heading", { level: 2, name: "Dương Thị Thảo" });
    await user.click(screen.getByRole("button", { name: /Đã nhận đủ 200.000/ }));
    await screen.findByText(formatAppMessage("MSG-48", { balance: "200.000 ₫" }));
    await tickPresent(user);
    await user.click(checkInButton()!);

    expect(
      await screen.findByText(
        "Không thể tiếp nhận lượt khám này. Vui lòng kiểm tra trạng thái lịch hẹn hoặc lượt khám.",
      ),
    ).toBeInTheDocument();
    expect(await screen.findByText(/đã được tiếp nhận lúc \d{2}:\d{2} · Số 12 · Phòng khám 2/)).toBeInTheDocument();
    expect(checkInButton()).toBeNull();
  });

  it("lịch đã tiếp nhận: nêu giờ, số và phòng trong panel, không có nút Tiếp nhận", async () => {
    renderPanel(appointmentTarget("apt-1020"));
    expect(await screen.findByRole("heading", { level: 2, name: "Đặng Thị Xuân" })).toBeInTheDocument();
    expect(
      screen.getByText("Lịch hẹn 08:30 hôm nay đã được tiếp nhận lúc 08:12 · Số 7 · Phòng khám 2."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem hàng đợi tiếp nhận" })).toHaveAttribute(
      "href",
      "/staff/reception-queue",
    );
    expect(checkInButton()).toBeNull();
  });

  it("lịch đã trả online: không có bước thu, chỉ còn thiếu xác nhận có mặt", async () => {
    const user = userEvent.setup();
    renderPanel(appointmentTarget("apt-1002"));
    await screen.findByRole("heading", { level: 2, name: "Trần Thị Bình" });
    expect(screen.getByText("Đã trả trực tuyến trước khi đến.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Đã nhận đủ/ })).toBeNull();
    expect(screen.getByText("Còn thiếu: xác nhận có mặt")).toBeInTheDocument();
    await tickPresent(user);
    expect(checkInButton()).toBeEnabled();
  });
});

describe("CheckInPanel: các cách tiếp nhận khác", () => {
  it("quay lại trong 7 ngày: không có khối thu phí, ghi không thu, tiếp nhận không cần phí", async () => {
    const user = userEvent.setup();
    renderPanel(patientTarget("pat-cuong"));
    await screen.findByRole("heading", { level: 2, name: "Lê Văn Cường" });
    expect(screen.getByRole("radio", { name: /Ngày 4\/7/ })).toBeChecked();
    expect(screen.getByText("Không thu")).toBeInTheDocument();
    expect(screen.getByText("Đang trong 7 ngày của lượt khám nên không thu thêm.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Đã nhận đủ|Tạo mã QR/ })).toBeNull();
    await tickPresent(user);
    expect(checkInButton()).toBeEnabled();
  });

  it("vãng lai: chưa chọn dịch vụ thì chưa có phí; chọn dịch vụ thì hiện đúng số tiền", async () => {
    const user = userEvent.setup();
    renderPanel(patientTarget("pat-moi"));
    await screen.findByRole("heading", { level: 2, name: "Đỗ Thanh Hà" });
    expect(screen.getByRole("radio", { name: /Khám vãng lai/ })).toBeChecked();
    expect(screen.getByText("Chọn dịch vụ khám để xem phí.")).toBeInTheDocument();
    expect(screen.getByText("Còn thiếu: chọn dịch vụ khám, xác nhận có mặt")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: /Khám cột sống/ }));
    expect(await screen.findByRole("button", { name: /Đã nhận đủ 250.000/ })).toBeInTheDocument();
    expect(screen.getByText("Còn thiếu: thu phí khám, xác nhận có mặt")).toBeInTheDocument();
  });

  it("lượt trước chưa hoàn tất quá 7 ngày: nêu lượt mới cần thu phí, vãng lai là lựa chọn duy nhất", async () => {
    renderPanel(patientTarget("pat-dung"));
    await screen.findByRole("heading", { level: 2, name: "Phạm Thị Dung" });
    expect(
      screen.getByText(
        "Lượt khám trước chưa hoàn tất và đã quá 7 ngày, nên lần này là lượt khám mới và cần thu phí khám.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Khám vãng lai/ })).toBeChecked();
    expect(screen.getByRole("button", { name: "Tiếp nhận" })).toBeDisabled();
  });

  it("không đến hôm qua và lịch hủy hôm nay: nêu lý do ngay trong panel, chỉ còn vãng lai", async () => {
    renderPanel(patientTarget("pat-em"));
    await screen.findByRole("heading", { level: 2, name: "Hoàng Văn Em" });
    expect(screen.getByText(/được ghi nhận là không đến.$/)).toBeInTheDocument();
    expect(screen.getByText(/đã bị hủy, không tiếp nhận được.$/)).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Khám vãng lai/ })).toBeChecked();
  });

  it("bệnh nhân có ngoài phạm vi (404 MSG-41): báo lỗi và yêu cầu bỏ lựa chọn", async () => {
    const handlers = renderPanel(patientTarget("pat-khong-co"));
    expect(
      await screen.findByText("Không tìm thấy thông tin cần xử lý hoặc thông tin không còn khả dụng."),
    ).toBeInTheDocument();
    expect(handlers.onInvalidTarget).toHaveBeenCalledTimes(1);
  });

  it("nhóm ưu tiên là lựa chọn của lễ tân: mặc định Không ưu tiên, chọn được nhóm từ cấu hình", async () => {
    const user = userEvent.setup();
    // Demo ≥ 75 tuổi: Nguyễn Thị Lan (90 tuổi), vãng lai. An là 34 tuổi nên không dùng cho nhóm này.
    renderPanel(patientTarget("pat-lan"));
    await screen.findByRole("heading", { level: 2, name: "Nguyễn Thị Lan" });
    const select = await screen.findByRole("combobox", { name: "Nhóm ưu tiên" });
    expect(select).toHaveValue("");
    expect(within(select).getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Không ưu tiên",
      "Cấp cứu",
      "Trẻ em dưới 6 tuổi",
      "Phụ nữ có thai",
      "Người khuyết tật nặng",
      "Người từ 75 tuổi",
      "Người có công với cách mạng",
    ]);
    await user.selectOptions(select, "AGE_75_PLUS");
    expect(select).toHaveValue("AGE_75_PLUS");
  });
});

