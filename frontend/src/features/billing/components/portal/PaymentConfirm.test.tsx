import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { createOnlinePayment, resetPatientBillingMock } from "../../api/patientBillingApi";
import { PaymentConfirm } from "./PaymentConfirm";
import { NO_REFUND_NOTICE } from "./patientBillingPresentation";
import { formatAppMessage } from "@/lib/messages/appMessages";
const { routerReplace } = vi.hoisted(() => ({ routerReplace: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: routerReplace,
    push: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Thông báo lỗi đi qua sonner; kiểm tra đúng câu MSG, không cần Toaster trong DOM.
vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

const MSG_49 = formatAppMessage("MSG-49");
const originalLocation = window.location;
let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetPatientBillingMock();
  routerReplace.mockReset();
  vi.mocked(toast.error).mockReset();
  assign = vi.fn();
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { ...originalLocation, assign },
  });
});

afterEach(() => {
  Object.defineProperty(window, "location", { configurable: true, value: originalLocation });
});

describe("PaymentConfirm", () => {
  it("nút Chuyển sang VNPay bị khóa cho tới khi tick đồng ý, và luôn hiện câu không hoàn tiền", async () => {
    const user = userEvent.setup();
    render(<PaymentConfirm id="vis-an-today" itemIds={["svc-cbc"]} kind="visit" />);

    const confirm = await screen.findByRole("button", { name: "Chuyển sang VNPay" });
    expect(confirm).toBeDisabled();
    expect(screen.getByText(NO_REFUND_NOTICE)).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Tôi đã đọc và đồng ý." }));
    expect(confirm).toBeEnabled();
    // Số tiền hiện ở dòng khoản và ở dòng tổng.
    expect(screen.getAllByText("250.000 ₫")).toHaveLength(2);
  });

  it("đồng ý và bấm: tạo giao dịch rồi chuyển sang đường dẫn BE trả về", async () => {
    const user = userEvent.setup();
    render(<PaymentConfirm id="vis-an-today" itemIds={["svc-cbc"]} kind="visit" />);
    await user.click(await screen.findByRole("checkbox", { name: "Tôi đã đọc và đồng ý." }));
    await user.click(screen.getByRole("button", { name: "Chuyển sang VNPay" }));

    expect(assign).toHaveBeenCalledWith(
      expect.stringMatching(/^\/portal\/payments\/result\?paymentId=pay-mock-\d+$/),
    );
    expect(routerReplace).not.toHaveBeenCalled();
  });

  it("409 (khoản đã được thanh toán ở nơi khác): báo MSG-49 và về chi tiết nguồn", async () => {
    // Tab khác đã tạo giao dịch cho cùng khoản: khoản này bị khóa.
    await createOnlinePayment({
      sourceKind: "visit",
      sourceId: "vis-an-today",
      itemIds: ["svc-cbc"],
    });
    const user = userEvent.setup();
    render(<PaymentConfirm id="vis-an-today" itemIds={["svc-cbc"]} kind="visit" />);
    await user.click(await screen.findByRole("checkbox", { name: "Tôi đã đọc và đồng ý." }));
    await user.click(screen.getByRole("button", { name: "Chuyển sang VNPay" }));

    expect(toast.error).toHaveBeenCalledWith(MSG_49, expect.anything());
    expect(routerReplace).toHaveBeenCalledWith("/portal/payments/visit/vis-an-today");
    expect(assign).not.toHaveBeenCalled();
  });

  it("khoản đã trả không đủ điều kiện: báo MSG-49 và chỉ có lối chọn lại", async () => {
    render(<PaymentConfirm id="vis-an-today" itemIds={["svc-xray-knee"]} kind="visit" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(MSG_49);
    expect(screen.getByRole("link", { name: "Chọn lại khoản" })).toHaveAttribute(
      "href",
      "/portal/payments/visit/vis-an-today",
    );
    expect(screen.queryByRole("button", { name: "Chuyển sang VNPay" })).not.toBeInTheDocument();
  });

  it("chưa chọn khoản nào thì không cho trả", async () => {
    render(<PaymentConfirm id="vis-an-today" itemIds={[]} kind="visit" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn chưa chọn khoản nào để thanh toán.",
    );
  });
});
