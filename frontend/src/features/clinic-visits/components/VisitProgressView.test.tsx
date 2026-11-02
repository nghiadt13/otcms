import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { getVisitProgress } from "../api/visitApi";
import { resetVisitMock } from "../api/visitMockStore";
import { VisitProgressView } from "./VisitProgressView";

const NOW = new Date("2026-10-24T08:00:00+07:00");

beforeEach(() => {
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
});

describe("VisitProgressView", () => {
  it("hai hàng đợi song song hiện đủ hai dòng; người đang được gọi ghi 'Đang gọi bạn'", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-lan-await")} />,
    );

    const queues = screen.getByRole("region", { name: "Bạn đang chờ" });
    const rows = within(queues).getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("Phòng X-quang 1");
    expect(rows[0]).toHaveTextContent("Còn 1 người trước bạn.");
    expect(rows[1]).toHaveTextContent("Phòng xét nghiệm");
    expect(rows[1]).toHaveTextContent("Đang gọi bạn");
    expect(rows[1]).toHaveTextContent("Đang phục vụ");
    expect(within(rows[1]).getByText("8")).toHaveClass("tabular-nums");
  });

  it("có chỉ định chờ thanh toán thì hiện nút thanh toán tới đúng lượt", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-lan-await")} />,
    );

    expect(
      screen.getByRole("link", {
        name: "Thanh toán các chỉ định chưa thanh toán",
      }),
    ).toHaveAttribute("href", "/portal/payments/visit/vis-lan-await");
  });

  it("không có chỉ định chờ thanh toán thì ẩn nút thanh toán", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-an-done")} />,
    );

    expect(
      screen.queryByRole("link", {
        name: "Thanh toán các chỉ định chưa thanh toán",
      }),
    ).not.toBeInTheDocument();
  });

  it("lượt hoàn tất: có link Xem kết quả, không có hàng đợi", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-an-done")} />,
    );

    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute(
      "href",
      "/portal/results/vis-an-done",
    );
    expect(
      screen.queryByRole("region", { name: "Bạn đang chờ" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(
        "Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.",
      ),
    ).not.toBeInTheDocument();
  });

  it("lượt đang chờ chưa có kết luận: nói rõ kết quả sẽ hiện ở đâu, không có link kết quả", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-an-today")} />,
    );

    expect(
      screen.queryByRole("link", { name: "Xem kết quả" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.",
      ),
    ).toBeInTheDocument();
  });

  it("lượt đã rời đi: nói rõ lý do, không có nút thanh toán hay hàng đợi", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-huy-left")} />,
    );

    expect(
      screen.getByText("Lượt khám đã đóng vì chưa được khám trong ngày."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("chờ kết luận: nhắc hạn quay lại đến hết ngày, lấy từ BE", async () => {
    render(
      <VisitProgressView progress={await getVisitProgress("vis-lan-await")} />,
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "đến hết ngày 29/10/2026, không mất phí khám lại.",
    );
  });
});
