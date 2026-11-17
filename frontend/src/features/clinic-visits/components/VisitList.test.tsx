import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { VisitListItem } from "../types/visitProgress";
import { VisitList } from "./VisitList";

// Ranh giới ngoài: URL của Next và mạng. Danh sách lượt chạy thật (listVisits lọc và chia trang); chỉ thay
// apiRequest bằng các dòng dựng sẵn của GET /api/v1/me/visits. Hồ sơ đã gắn lấy từ kho hồ sơ giả.
const nav = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  search: "",
}));
const request = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  usePathname: () => "/portal/visits",
  useSearchParams: () => new URLSearchParams(nav.search),
}));
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

function profileIdOf(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

type RowPatch = Pick<VisitListItem, "visitId" | "status" | "visitDate"> &
  Partial<VisitListItem>;

/** Một dòng của GET /api/v1/me/visits; người khám lấy từ hồ sơ đã gắn. */
function row(patientCode: string, patch: RowPatch): VisitListItem {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return {
    patientProfileId: profile.id,
    patientName: profile.fullName,
    relationship: profile.relationship,
    checkedInAt: `${patch.visitDate}T01:00:00Z`,
    queueNumber: null,
    roomName: null,
    ...patch,
  };
}

/** Năm lượt, mới trước (thứ tự BE). */
const visitRows = (): VisitListItem[] => [
  row("BN-000123", {
    visitId: "vis-an-today",
    visitDate: "2026-10-24",
    status: "WAITING_FOR_EXAMINATION",
    queueNumber: 12,
    roomName: "Phòng khám 2",
  }),
  row("BN-000456", {
    visitId: "vis-lan-await",
    visitDate: "2026-10-23",
    status: "AWAITING_CONCLUSION",
  }),
  row("BN-000123", {
    visitId: "vis-huy-left",
    visitDate: "2026-10-22",
    status: "CANCELLED_LEFT",
  }),
  row("BN-000123", {
    visitId: "vis-an-done",
    visitDate: "2026-10-20",
    status: "COMPLETED",
  }),
  row("BN-000456", {
    visitId: "vis-lan-incomplete",
    visitDate: "2026-10-18",
    status: "INCOMPLETE",
  }),
];

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
  request.mockReset();
  request.mockImplementation(async (path: string) => {
    if (path !== "/api/v1/me/visits")
      throw new Error(`đường dẫn không mong đợi: ${path}`);
    return visitRows();
  });
});

describe("VisitList", () => {
  it("tab Đang diễn ra mặc định: chỉ lượt đang chờ hoặc đang khám, mỗi dòng là link tới chi tiết", async () => {
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

  it("?tab=recent hiện mọi lượt trong danh sách, kể cả đã xong và đã rời đi", async () => {
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

  it("không có lượt nào của người khám thì nói cách dùng, không viết 'Không có dữ liệu'", async () => {
    nav.search = `profileId=${profileIdOf("BN-000789")}`;
    render(<VisitList />);

    expect(
      await screen.findByText(
        "Hôm nay bạn chưa có lượt khám nào. Khi lễ tân tiếp nhận, số thứ tự sẽ hiện ở đây.",
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải báo rõ và Thử lại lấy lại được danh sách", async () => {
    request.mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
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
