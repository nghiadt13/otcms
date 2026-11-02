import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { listVisits } from "../api/visitApi";
import { resetVisitMock } from "../api/visitMockStore";
import type { VisitSummary } from "../types/visitProgress";
import { ActiveVisitCard } from "./ActiveVisitCard";

const NOW = new Date("2026-10-24T08:00:00+07:00");

async function activeVisitOf(id: string): Promise<VisitSummary> {
  const page = await listVisits({ when: "active", page: 0, size: 10 });
  const visit = page.items.find((item) => item.id === id);
  if (!visit) throw new Error(`thiếu lượt ${id}`);
  return visit;
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
});

describe("ActiveVisitCard", () => {
  it("hiện người khám, trạng thái, số thứ tự và số người phía trước bằng chữ", async () => {
    render(<ActiveVisitCard visit={await activeVisitOf("vis-an-today")} />);

    expect(
      await screen.findByText("Nguyễn Văn An · Bản thân"),
    ).toBeInTheDocument();
    expect(screen.getByText("Chờ khám")).toBeInTheDocument();
    expect(
      await screen.findByText("Còn 3 người trước bạn."),
    ).toBeInTheDocument();
    expect(screen.getByText("Số thứ tự")).toBeInTheDocument();
    expect(screen.getByText("12")).toHaveClass("tabular-nums");
  });

  it("nút Xem tiến độ dẫn tới chi tiết lượt và gọi tên người khám", async () => {
    render(<ActiveVisitCard visit={await activeVisitOf("vis-an-today")} />);

    const link = await screen.findByRole("link", {
      name: "Xem tiến độ của Nguyễn Văn An",
    });
    expect(link).toHaveAttribute("href", "/portal/visits/vis-an-today");
  });

  it("chỉ lượt đầu tiên là nút chính; lượt khác là nút phụ", async () => {
    const visit = await activeVisitOf("vis-an-today");
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
