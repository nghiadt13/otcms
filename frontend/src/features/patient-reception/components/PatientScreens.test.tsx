import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReceptionProvider } from "./ReceptionProvider";
import { ReceptionScreen } from "./ReceptionScreen";
import { createReceptionSeed } from "../api/receptionSeed";
import { loadReception, resetReceptionMock } from "../api/receptionApi";
import { formatAppMessage } from "@/lib/messages/appMessages";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
beforeEach(() => resetReceptionMock(createReceptionSeed("2026-10-27")));
async function mount() {
  render(
    <ReceptionProvider>
      <ReceptionScreen section="patients" />
    </ReceptionProvider>,
  );
  await screen.findByRole("heading", { name: "Hồ sơ bệnh nhân" });
}
function search(q: string) {
  fireEvent.change(
    screen.getByLabelText("Tên, số điện thoại hoặc mã bệnh nhân"),
    { target: { value: q } },
  );
  fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
}
function fillProfile(
  dialog: HTMLElement,
  input: { name: string; dob: string; phone: string },
) {
  for (const [label, value] of [
    ["Họ và tên *", input.name],
    ["Ngày sinh *", input.dob],
    ["Số điện thoại *", input.phone],
    ["Địa chỉ *", "Địa chỉ mẫu kiểm thử"],
  ]) {
    fireEvent.change(within(dialog).getByLabelText(label), {
      target: { value },
    });
  }
}

describe("patient search and walk-in creation with the real mock adapter", () => {
  it.each(["Bình", "binh", "0900000002", "+84 900 000 002", "bn-0002"])(
    "finds the patient using only %s",
    async (q) => {
      await mount();
      search(q);
      expect(screen.getByText("Trần Văn Bình")).toBeInTheDocument();
      expect(screen.getByText("12/08/1948")).toBeInTheDocument();
      expect(screen.queryByText("Nguyễn Minh Anh")).not.toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent("1 hồ sơ phù hợp");
    },
  );
  it("shows candidates for a shared name/phone and uses birthdate to narrow them without selecting automatically", async () => {
    const state = createReceptionSeed("2026-10-27");
    state.patients.push({
      ...state.patients[1],
      id: "BN0010",
      name: "Lê Quốc Bình",
      dob: "1990-05-05",
    });
    resetReceptionMock(state);
    await mount();
    search("Bình");
    expect(screen.getAllByRole("button", { name: "Mở hồ sơ" })).toHaveLength(2);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Ngày sinh (không bắt buộc)"), {
      target: { value: "1948-08-12" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    expect(screen.getAllByRole("button", { name: "Mở hồ sơ" })).toHaveLength(1);
    expect(screen.queryByText("Lê Quốc Bình")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    expect(screen.queryByText("Trần Văn Bình")).not.toBeInTheDocument();
    search("0900000002");
    expect(screen.getAllByRole("button", { name: "Mở hồ sơ" })).toHaveLength(2);
    const row = screen.getByText("Lê Quốc Bình").closest("tr")!;
    fireEvent.click(within(row).getByRole("button", { name: "Mở hồ sơ" }));
    const dialog = await screen.findByRole("dialog", { name: "Lê Quốc Bình" });
    expect(within(dialog).getByText(/05\/05\/1990/)).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/dị ứng|tiền sử/i),
    ).not.toBeInTheDocument();
  });
  it("keeps creation available for an empty or invalid search without showing all patient records", async () => {
    await mount();
    expect(screen.getByRole("button", { name: "Tạo hồ sơ" })).toBeEnabled();
    expect(screen.queryByText("Trần Văn Bình")).not.toBeInTheDocument();
    search("Bình");
    expect(screen.getByText("Trần Văn Bình")).toBeInTheDocument();
    search("---");
    expect(screen.queryByText("Trần Văn Bình")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-54"),
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    const dialog = await screen.findByRole("dialog", {
      name: "Tạo hồ sơ khám trực tiếp",
    });
    expect(within(dialog).getByLabelText("Số điện thoại *")).toHaveValue("");
    expect(
      within(dialog).getByRole("button", { name: "Tạo hồ sơ" }),
    ).toBeDisabled();
  });
  it("creates a profile without a prior lookup and shows the new record after saving", async () => {
    const user = userEvent.setup();
    await mount();
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    fillProfile(dialog, {
      name: "Bệnh Nhân Mới",
      dob: "1990-05-05",
      phone: "0900000088",
    });
    await user.click(within(dialog).getByRole("checkbox"));
    await user.click(within(dialog).getByRole("button", { name: "Tạo hồ sơ" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("Bệnh Nhân Mới")).toBeInTheDocument();
    const state = await loadReception();
    expect(state.patients).toHaveLength(10);
    expect(state.patients[0].name).toBe("Bệnh Nhân Mới");
  });
  it("rejects duplicate creation even when the form is opened before searching", async () => {
    const user = userEvent.setup();
    await mount();
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    fillProfile(dialog, {
      name: "Trần Văn Bình",
      dob: "1948-08-12",
      phone: "+84900000002",
    });
    await user.click(within(dialog).getByRole("checkbox"));
    await user.click(within(dialog).getByRole("button", { name: "Tạo hồ sơ" }));
    await waitFor(() =>
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        formatAppMessage("MSG-18"),
      ),
    );
    expect((await loadReception()).patients).toHaveLength(9);
  });
  it("prefills only a complete phone number and optional birthdate when creating from a search", async () => {
    await mount();
    search("+84 900 000 088");
    fireEvent.change(screen.getByLabelText("Ngày sinh (không bắt buộc)"), {
      target: { value: "1990-05-05" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText("Số điện thoại *")).toHaveValue(
      "0900000088",
    );
    expect(within(dialog).getByLabelText("Ngày sinh *")).toHaveValue(
      "1990-05-05",
    );
    expect(within(dialog).getByLabelText("Họ và tên *")).toHaveValue("");
  });
});
