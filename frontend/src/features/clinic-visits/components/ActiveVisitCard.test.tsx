import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getVisitProgress } from "../api/visitApi";
import type { VisitProgress, VisitSummary } from "../types/visitProgress";
import { ActiveVisitCard } from "./ActiveVisitCard";

// Tiến độ là API thật nên mock ở ranh giới getVisitProgress; lượt là dữ liệu dựng sẵn (không gọi danh sách).
vi.mock("../api/visitApi", () => ({ getVisitProgress: vi.fn() }));

const visit: VisitSummary = {
  id: "vis-an-today",
  visitDate: "2026-10-24",
  status: "WAITING_FOR_EXAMINATION",
  patient: { id: "p-an", fullName: "Nguyễn Văn An", relationship: "SELF" },
};

const progress: VisitProgress = {
  visitId: "vis-an-today",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: "CONSULTATION",
  arrivedAt: "2026-10-24T01:00:00Z",
  queuePosition: null,
  currentRoom: "Phòng khám 2",
  queueNumber: 12,
  queueStage: "EXAM",
  examQueue: null,
  peopleAhead: 3,
  beingCalled: false,
  serviceOrders: [],
};

beforeEach(() => {
  vi.mocked(getVisitProgress).mockReset();
  vi.mocked(getVisitProgress).mockResolvedValue(progress);
});

describe("ActiveVisitCard", () => {
  it("hiện người khám, trạng thái, số thứ tự và số người phía trước bằng chữ", async () => {
    render(<ActiveVisitCard visit={visit} />);

    expect(
      await screen.findByText("Nguyễn Văn An · Bản thân"),
    ).toBeInTheDocument();
    expect(screen.getByText("Chờ khám")).toBeInTheDocument();
    expect(
      await screen.findByText("Còn 3 người trước bạn."),
    ).toBeInTheDocument();
    expect(screen.getByText("Số thứ tự")).toBeInTheDocument();
    expect(screen.getByText("12")).toHaveClass("tabular-nums");
    expect(getVisitProgress).toHaveBeenCalledWith(
      "vis-an-today",
      expect.any(AbortSignal),
    );
  });

  it("chưa vào hàng thì ghi không chờ ở phòng nào", async () => {
    vi.mocked(getVisitProgress).mockResolvedValue({
      ...progress,
      queueNumber: null,
      peopleAhead: null,
      currentRoom: null,
    });
    render(<ActiveVisitCard visit={visit} />);

    expect(
      await screen.findByText("Hiện bạn không chờ ở phòng nào."),
    ).toBeInTheDocument();
  });

  it("nút Xem tiến độ dẫn tới chi tiết lượt và gọi tên người khám", async () => {
    render(<ActiveVisitCard visit={visit} />);

    const link = await screen.findByRole("link", {
      name: "Xem tiến độ của Nguyễn Văn An",
    });
    expect(link).toHaveAttribute("href", "/portal/visits/vis-an-today");
  });

  it("chỉ lượt đầu tiên là nút chính; lượt khác là nút phụ", async () => {
    const { rerender } = render(<ActiveVisitCard primary visit={visit} />);
    expect(
      await screen.findByRole("link", {
        name: "Xem tiến độ của Nguyễn Văn An",
      }),
    ).toHaveClass("bg-primary");

    rerender(<ActiveVisitCard visit={visit} />);
    expect(
      screen.getByRole("link", { name: "Xem tiến độ của Nguyễn Văn An" }),
    ).not.toHaveClass("bg-primary");
  });
});
