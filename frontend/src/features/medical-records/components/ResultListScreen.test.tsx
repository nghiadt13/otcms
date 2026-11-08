import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { ResultListScreen } from "./ResultListScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Ranh giới ngoài: router của Next; `search` giả lập `?profileId=` trên URL.
const nav = vi.hoisted(() => ({ replace: vi.fn(), search: "" }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: vi.fn() }),
  usePathname: () => "/portal/results",
  useSearchParams: () => new URLSearchParams(nav.search),
}));

const LAN_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02";
const HUY_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f03";

beforeEach(() => {
  nav.search = "";
  nav.replace.mockClear();
  resetLinkedProfilesMock();
});

afterEach(() => {
  resetLinkedProfilesMock();
});

describe("ResultListScreen", () => {
  it("liệt kê lượt của mọi hồ sơ, mỗi dòng nói rõ người khám và quan hệ", async () => {
    render(<ResultListScreen />);
    expect(
      await screen.findByRole("link", { name: /Khám chỉnh hình tổng quát/ }),
    ).toHaveAttribute("href", "/portal/results/vis-an-done");
    // Mỗi người có hai lượt trong dữ liệu giả, nên mỗi tên có hai thẻ.
    expect(screen.getAllByRole("link", { name: /Nguyễn Văn An/ })).toHaveLength(
      2,
    );
    expect(
      screen.getAllByRole("link", { name: /Nguyễn Thị Lan/ }),
    ).toHaveLength(2);
    // Tên cũng nằm trong ô chọn người khám; chỉ tính trong các thẻ lượt khám.
    const cardTexts = screen
      .getAllByRole("article")
      .map((card) => card.textContent ?? "");
    expect(
      cardTexts.filter((text) => text.includes("Nguyễn Văn An · Bản thân")),
    ).toHaveLength(2);
    expect(
      cardTexts.filter((text) => text.includes("Nguyễn Thị Lan · Cha/Mẹ")),
    ).toHaveLength(2);
    expect(screen.getByLabelText("Người khám")).toHaveValue("");
  });

  it("đầu trang có ba số liệu và danh sách nhóm theo tháng, mới nhất trước", async () => {
    render(<ResultListScreen />);
    await screen.findByRole("link", { name: /Khám chỉnh hình tổng quát/ });

    const cards = screen.getAllByRole("term").map((term) => ({
      label: term.textContent,
      value: term.previousElementSibling?.textContent,
    }));
    expect(cards).toEqual([
      { label: "Lượt khám", value: "04" },
      { label: "Có kết quả", value: "03" },
      { label: "Chưa hoàn tất", value: "01" },
    ]);

    expect(
      screen
        .getAllByRole("heading", { level: 3 })
        .map((heading) => heading.textContent),
    ).toEqual([
      "Tháng 10, 2026",
      "Tháng 9, 2026",
      "Tháng 7, 2026",
      "Tháng 6, 2026",
    ]);
    expect(screen.getByText("Mới nhất trước")).toBeInTheDocument();
  });

  it("thẻ có kết quả dẫn tới 'Xem kết quả', thẻ chưa hoàn tất chỉ có 'Chi tiết'", async () => {
    render(<ResultListScreen />);
    await screen.findByRole("link", { name: /Khám chỉnh hình tổng quát/ });
    expect(screen.getAllByText("Có kết quả").length).toBeGreaterThan(0);
    expect(screen.getByText("Kết quả chưa được hoàn tất")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /^Chi tiết.*Khám chỉnh hình,/ }),
    ).toHaveAttribute("href", "/portal/results/vis-lan-incomplete");
    expect(
      screen.getByRole("link", {
        name: /^Xem kết quả.*Khám chỉnh hình tổng quát/,
      }),
    ).toHaveAttribute("href", "/portal/results/vis-an-done");
  });

  it("tab Đang xử lý chỉ còn lượt chưa hoàn tất", async () => {
    const user = userEvent.setup();
    render(<ResultListScreen />);
    await user.click(
      await screen.findByRole("tab", { name: /Đang xử lý \(1\)/ }),
    );
    expect(screen.getAllByRole("link")).toHaveLength(
      screen.getAllByRole("link", { name: /^Chi tiết/ }).length,
    );
    expect(
      screen.queryByText("Kết luận đã được công bố"),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Kết quả chưa được hoàn tất")).toBeInTheDocument();
  });

  it("lọc theo người khám đang có trên URL", async () => {
    nav.search = `profileId=${LAN_ID}`;
    render(<ResultListScreen />);
    expect(
      await screen.findAllByRole("link", { name: /Nguyễn Thị Lan/ }),
    ).toHaveLength(2);
    expect(
      screen.queryByRole("link", { name: /Nguyễn Văn An/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("Người khám")).toHaveValue(LAN_ID);
  });

  it("đổi người khám đưa mã hồ sơ lên URL", async () => {
    const user = userEvent.setup();
    render(<ResultListScreen />);
    await user.selectOptions(
      await screen.findByLabelText("Người khám"),
      HUY_ID,
    );
    expect(nav.replace).toHaveBeenCalledWith(
      `/portal/results?profileId=${HUY_ID}`,
    );
  });

  it("hồ sơ chưa có lượt nào hiện trạng thái trống có câu dạy cách dùng", async () => {
    nav.search = `profileId=${HUY_ID}`;
    render(<ResultListScreen />);
    expect(
      await screen.findByText(/Chưa có kết quả khám nào\. Kết quả hiện ở đây/),
    ).toBeInTheDocument();
  });

  it("lỗi tải danh sách hiện thông báo kèm nút thử lại", async () => {
    nav.search = "profileId=ERR-500";
    render(<ResultListScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });

  it("tài khoản chưa có thông tin cá nhân được dẫn tới trang điền, không hiện danh sách", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<ResultListScreen />);
    expect(
      await screen.findByText(/Tài khoản của bạn chưa có thông tin cá nhân/),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /Khám chỉnh hình tổng quát/ }),
    ).not.toBeInTheDocument();
  });
});
