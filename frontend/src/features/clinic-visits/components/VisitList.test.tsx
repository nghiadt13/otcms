import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { listVisits } from "../api/visitApi";
import { resetVisitMock } from "../api/visitMockStore";
import { VisitList } from "./VisitList";

// Ranh giới ngoài: URL của Next. Lớp API giả chạy thật; chỉ bọc listVisits để ép một lần lỗi.
const nav = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  search: "",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  usePathname: () => "/portal/visits",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("../api/visitApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/visitApi")>();
  return { ...original, listVisits: vi.fn(original.listVisits) };
});

const NOW = new Date("2026-10-24T08:00:00+07:00");

function profileIdOf(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

/** Các dòng lượt khám (cả dòng là link tới chi tiết). */
const visitLinks = () =>
  screen
    .queryAllByRole("link")
    .map((link) => link.getAttribute("href") ?? "")
    .filter((href) => href.startsWith("/portal/visits/vis-"));

beforeEach(() => {
  nav.search = "";
  nav.push.mockClear();
  nav.replace.mockClear();
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
  vi.mocked(listVisits).mockClear();
});

describe("VisitList", () => {
  it("tab Đang diễn ra mặc định: hai lượt đang khám, mỗi dòng là link tới chi tiết", async () => {
    render(<VisitList />);

    await waitFor(() =>
      expect(visitLinks()).toEqual([
        "/portal/visits/vis-an-today",
        "/portal/visits/vis-lan-await",
      ]),
    );
    expect(screen.getByRole("tab", { name: "Đang diễn ra" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("?tab=recent hiện cả lượt đã xong và đã rời đi trong 30 ngày", async () => {
    nav.search = "tab=recent";
    render(<VisitList />);

    await waitFor(() => expect(visitLinks()).toHaveLength(5));
    expect(visitLinks()).toContain("/portal/visits/vis-huy-left");
    expect(visitLinks()).toContain("/portal/visits/vis-an-done");
  });

  it("?profileId= lọc theo người khám đã gắn", async () => {
    nav.search = `profileId=${profileIdOf("BN-000456")}`;
    render(<VisitList />);

    await waitFor(() =>
      expect(visitLinks()).toEqual(["/portal/visits/vis-lan-await"]),
    );
    expect(screen.getByRole("combobox", { name: "Người khám" })).toHaveValue(
      profileIdOf("BN-000456"),
    );
  });

  it("không có lượt đang diễn ra thì nói cách dùng, không viết 'Không có dữ liệu'", async () => {
    nav.search = `profileId=${profileIdOf("BN-000789")}`;
    render(<VisitList />);

    expect(
      await screen.findByText(
        "Hôm nay bạn chưa có lượt khám nào. Khi lễ tân tiếp nhận, số thứ tự sẽ hiện ở đây.",
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải báo rõ và Thử lại lấy lại được danh sách", async () => {
    vi.mocked(listVisits).mockRejectedValueOnce(
      new ApiError(500, { code: "MSG-04" }),
    );
    render(<VisitList />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không tải được lượt khám.");
    await userEvent.click(
      within(alert.parentElement as HTMLElement).getByRole("button", {
        name: "Thử lại",
      }),
    );

    await waitFor(() => expect(visitLinks()).toHaveLength(2));
  });

  it("chuyển tab đẩy lịch sử với ?tab=recent", async () => {
    render(<VisitList />);
    await waitFor(() => expect(visitLinks()).toHaveLength(2));

    await userEvent.click(screen.getByRole("tab", { name: "Gần đây" }));
    expect(nav.push).toHaveBeenCalledWith("/portal/visits?tab=recent");
  });
});
