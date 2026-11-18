import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { VisitProgress } from "../types/visitProgress";
import { VisitProgressView } from "./VisitProgressView";

// Tiến độ là dữ liệu BE (không có kết quả khám). Các nút chỉ là gợi ý suy từ chỉ định.
const progress = (patch: Partial<VisitProgress> = {}): VisitProgress => ({
  visitId: "visit-1",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: "CONSULTATION",
  arrivedAt: "2026-10-24T01:00:00Z",
  queuePosition: null,
  currentRoom: "Phòng khám 2",
  queueNumber: 12,
  peopleAhead: 3,
  beingCalled: false,
  serviceOrders: [],
  ...patch,
});

const order = (
  patch: Partial<VisitProgress["serviceOrders"][number]> = {},
) => ({
  orderId: "o-1",
  orderCode: "CLS-1",
  serviceName: "MRI cột sống",
  status: "ORDERED" as const,
  paymentStatus: "PENDING_PAYMENT" as const,
  roomId: null,
  roomName: null,
  ...patch,
});

describe("VisitProgressView", () => {
  it("đang chờ: hiện phòng, số thứ tự và còn bao nhiêu người trước bạn", () => {
    render(<VisitProgressView progress={progress()} />);

    expect(
      screen.getByRole("heading", { name: "Bạn đang chờ" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Phòng khám 2")).toBeInTheDocument();
    expect(screen.getByText("Còn 3 người trước bạn.")).toBeInTheDocument();
    expect(screen.getByText("12")).toHaveClass("tabular-nums");
  });

  it("đang được gọi thì ghi Đang gọi bạn; không còn ai trước thì báo người tiếp theo", () => {
    const { rerender } = render(
      <VisitProgressView
        progress={progress({ beingCalled: true, peopleAhead: 0 })}
      />,
    );
    expect(screen.getByText("Đang gọi bạn")).toBeInTheDocument();

    rerender(<VisitProgressView progress={progress({ peopleAhead: 0 })} />);
    expect(screen.getByText("Bạn là người tiếp theo.")).toBeInTheDocument();
  });

  it("chưa vào hàng và không có chỉ định thì không hiện mục hàng đợi", () => {
    render(
      <VisitProgressView
        progress={progress({
          queueNumber: null,
          peopleAhead: null,
          currentRoom: null,
        })}
      />,
    );

    expect(
      screen.queryByRole("heading", { name: "Bạn đang chờ" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("Hiện bạn không chờ ở phòng nào."),
    ).toBeInTheDocument();
  });

  it("chỉ định: hiện tên, phòng (có hay chưa), trạng thái và thanh toán bằng chữ", () => {
    render(
      <VisitProgressView
        progress={progress({
          serviceOrders: [
            order(),
            order({
              orderId: "o-2",
              serviceName: "Xét nghiệm máu",
              status: "RESULTS_AVAILABLE",
              paymentStatus: "PAID",
              roomId: "room-lab",
              roomName: "Phòng xét nghiệm",
            }),
          ],
        })}
      />,
    );

    const list = screen.getByRole("list");
    const [first, second] = within(list).getAllByRole("listitem");
    expect(within(first).getByText("MRI cột sống")).toBeInTheDocument();
    expect(
      within(first).getByText("Chưa có phòng thực hiện"),
    ).toBeInTheDocument();
    expect(within(first).getByText("Đã chỉ định")).toBeInTheDocument();
    expect(within(first).getByText("Chưa thanh toán")).toBeInTheDocument();
    expect(
      within(second).getByText("Phòng thực hiện: Phòng xét nghiệm"),
    ).toBeInTheDocument();
    expect(within(second).getByText("Đã có kết quả")).toBeInTheDocument();
    expect(within(second).getByText("Đã thanh toán")).toBeInTheDocument();
  });

  it("có chỉ định chưa thanh toán thì có lối tới trang thanh toán lượt", () => {
    render(
      <VisitProgressView progress={progress({ serviceOrders: [order()] })} />,
    );

    expect(
      screen.getByRole("link", {
        name: "Thanh toán các chỉ định chưa thanh toán",
      }),
    ).toHaveAttribute("href", PATIENT_ROUTES.payments);
  });

  it("chỉ định đã hủy không còn cần thanh toán", () => {
    render(
      <VisitProgressView
        progress={progress({
          serviceOrders: [order({ status: "CANCELLED" })],
        })}
      />,
    );

    expect(
      screen.queryByRole("link", {
        name: "Thanh toán các chỉ định chưa thanh toán",
      }),
    ).not.toBeInTheDocument();
  });

  it("có kết quả sẵn sàng thì mở khu kết quả; khi chưa có thì ghi chú đợi bác sĩ kết luận", () => {
    const { rerender } = render(
      <VisitProgressView
        progress={progress({
          serviceOrders: [
            order({ status: "RESULTS_AVAILABLE", paymentStatus: "PAID" }),
          ],
        })}
      />,
    );
    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute(
      "href",
      PATIENT_ROUTES.resultDetail("visit-1"),
    );
    expect(
      screen.queryByText(
        "Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.",
      ),
    ).not.toBeInTheDocument();

    rerender(<VisitProgressView progress={progress()} />);
    expect(
      screen.queryByRole("link", { name: "Xem kết quả" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.",
      ),
    ).toBeInTheDocument();
  });
});
