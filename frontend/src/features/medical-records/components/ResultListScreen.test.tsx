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
    // Mỗi người có hai lượt trong dữ liệu giả, nên mỗi tên có hai dòng.
    expect(
      screen.getAllByRole("link", { name: /Nguyễn Văn An \(Bản thân\)/ }),
    ).toHaveLength(2);
    expect(
      screen.getAllByRole("link", { name: /Nguyễn Thị Lan \(Cha\/Mẹ\)/ }),
    ).toHaveLength(2);
    expect(screen.getByLabelText("Người khám")).toHaveValue("");
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

  it("tài khoản chưa có hồ sơ được dẫn tới thêm hồ sơ, không hiện danh sách", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<ResultListScreen />);
    expect(
      await screen.findByText(/Tài khoản của bạn chưa có hồ sơ bệnh nhân/),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /Khám chỉnh hình tổng quát/ }),
    ).not.toBeInTheDocument();
  });
});
