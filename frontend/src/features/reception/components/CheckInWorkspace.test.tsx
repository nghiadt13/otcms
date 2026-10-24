import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetReceptionMockStore } from "../api/receptionMockStore";
import { CheckInWorkspace } from "./CheckInWorkspace";

// Ngữ cảnh nằm ở URL: giả lập next/navigation bằng biến trong test (đọc tham số, ghi bằng replace).
const nav = vi.hoisted(() => ({ search: "", replace: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  useSearchParams: () => new URLSearchParams(nav.search),
  usePathname: () => "/staff/check-in",
}));

/** Giả lập viewport: wide = từ lg (1024px) trở lên. */
function stubViewport(wide: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: wide,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

// Dòng PatientLookup giả: chỉ cần ô SĐT (type tel) để kiểm phím "/" đưa con trỏ vào đây.
const lookupSlot = (
  <div>
    <label htmlFor="phone-field">Số điện thoại</label>
    <input id="phone-field" type="tel" />
  </div>
);

function renderWorkspace() {
  return render(<CheckInWorkspace lookupSlot={lookupSlot} />);
}

const rowButton = (name: RegExp) => screen.findByRole("button", { name });
const filterBox = () => screen.getByRole("searchbox", { name: "Lọc tên hoặc số điện thoại" });
const panelRegion = () => screen.getByRole("region", { name: "Tiếp nhận" });

beforeEach(() => {
  resetReceptionMockStore();
  nav.search = "";
  nav.replace.mockClear();
  stubViewport(true);
});

describe("CheckInWorkspace: danh sách và đích chọn", () => {
  it("hiện tab đếm, danh sách lịch chờ và gợi ý khi chưa chọn ai", async () => {
    renderWorkspace();
    expect(await screen.findByRole("tab", { name: /Chờ tiếp nhận\s*14/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Đã tiếp nhận\s*9/ })).toBeInTheDocument();
    expect(await rowButton(/Nguyễn Văn An/)).toBeInTheDocument();
    expect(screen.getByText(/Chọn một lịch hẹn bên trái/)).toBeInTheDocument();
  });

  it("chọn một dòng đưa lịch vào URL (appointmentId) và đánh dấu dòng đó", async () => {
    const user = userEvent.setup();
    nav.search = "appointmentId=apt-1002";
    renderWorkspace();
    await user.click(await rowButton(/Nguyễn Văn An/));
    expect(nav.replace).toHaveBeenCalledWith("/staff/check-in?appointmentId=apt-1001", { scroll: false });
    expect(await rowButton(/Trần Thị Bình/)).toHaveAttribute("aria-current", "true");
  });

  it("tab Đã tiếp nhận đổi danh sách sang lịch đã có số", async () => {
    const user = userEvent.setup();
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    await user.click(screen.getByRole("tab", { name: /Đã tiếp nhận/ }));
    expect(await rowButton(/Đặng Thị Xuân/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Nguyễn Văn An/ })).toBeNull();
  });

  it("ô lọc tìm không phân biệt dấu trên tên đã tải, không gửi lên máy chủ", async () => {
    const user = userEvent.setup();
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    await user.type(filterBox(), "binh");
    expect(await rowButton(/Trần Thị Bình/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Nguyễn Văn An/ })).toBeNull();
  });

  it("bệnh nhân từ ô tìm (patientId) mở panel ngay trong cột phải", async () => {
    nav.search = "patientId=pat-cuong";
    renderWorkspace();
    const panel = panelRegion();
    expect(await within(panel).findByRole("heading", { level: 2, name: "Lê Văn Cường" })).toBeInTheDocument();
  });
});

describe("CheckInWorkspace: bàn phím", () => {
  it("phím / đưa con trỏ vào ô SĐT khi không đang gõ", async () => {
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    fireEvent.keyDown(document.body, { key: "/" });
    expect(document.getElementById("phone-field")).toHaveFocus();
  });

  it("phím / không chen vào khi đang gõ trong ô lọc", async () => {
    const user = userEvent.setup();
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    await user.click(filterBox());
    await user.keyboard("/");
    expect(filterBox()).toHaveFocus();
    expect(filterBox()).toHaveValue("/");
  });

  it("↑↓ di chuyển focus giữa các dòng của danh sách", async () => {
    const user = userEvent.setup();
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    const rows = Array.from(document.querySelectorAll<HTMLButtonElement>("button[data-row]"));
    expect(rows.length).toBeGreaterThan(2);
    rows[0].focus();
    await user.keyboard("{ArrowDown}");
    expect(rows[1]).toHaveFocus();
    await user.keyboard("{ArrowUp}");
    expect(rows[0]).toHaveFocus();
  });
});

describe("CheckInWorkspace: dưới lg dùng Sheet", () => {
  it("có đích trên URL thì mở panel trong Sheet; Esc đóng và xóa tham số", async () => {
    const user = userEvent.setup();
    stubViewport(false);
    nav.search = "appointmentId=apt-1001";
    renderWorkspace();
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByRole("heading", { level: 2, name: "Nguyễn Văn An" })).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(nav.replace).toHaveBeenCalledWith("/staff/check-in", { scroll: false });
  });

  it("không có đích thì không mở Sheet và không có cột phải", async () => {
    stubViewport(false);
    renderWorkspace();
    await rowButton(/Nguyễn Văn An/);
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.queryByRole("region", { name: "Tiếp nhận" })).toBeNull();
  });
});

describe("CheckInWorkspace: tiếp nhận xong", () => {
  it("sau khi tiếp nhận, lịch rời tab Chờ tiếp nhận; Tiếp nhận người tiếp theo xóa đích và đưa con trỏ về ô SĐT", async () => {
    const user = userEvent.setup();
    nav.search = "appointmentId=apt-1001";
    renderWorkspace();
    const panel = panelRegion();
    await within(panel).findByRole("heading", { level: 2, name: "Nguyễn Văn An" });

    await user.click(within(panel).getByRole("button", { name: /Đã nhận đủ 200.000/ }));
    await user.click(within(panel).getByRole("checkbox", { name: /Đã kiểm tra đúng người/ }));
    await user.click(within(panel).getByRole("button", { name: "Tiếp nhận" }));
    expect(await within(panel).findByRole("heading", { level: 2, name: "Đã tiếp nhận Nguyễn Văn An" })).toBeInTheDocument();

    await waitFor(() => expect(screen.queryByRole("button", { name: /Nguyễn Văn An/ })).toBeNull());
    await user.click(within(panel).getByRole("button", { name: "Tiếp nhận người tiếp theo" }));
    expect(nav.replace).toHaveBeenCalledWith("/staff/check-in", { scroll: false });
    expect(document.getElementById("phone-field")).toHaveFocus();
  });
});
