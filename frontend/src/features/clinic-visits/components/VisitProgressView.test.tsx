import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type {
  VisitProgress,
  VisitProgressOrder,
  VisitQueueInfo,
  VisitSummary,
} from "../types/visitProgress";
import { VisitProgressView } from "./VisitProgressView";

// Tiến độ là dữ liệu BE (không có kết quả khám). Các nút chỉ là gợi ý suy từ chỉ định.
const queue = (patch: Partial<VisitQueueInfo> = {}): VisitQueueInfo => ({
  queueNumber: 12,
  status: "WAITING",
  roomCode: "Phòng khám 2",
  peopleAhead: 3,
  beingCalled: false,
  ...patch,
});

const progress = (patch: Partial<VisitProgress> = {}): VisitProgress => ({
  visitId: "visit-1",
  source: "SCHEDULED",
  status: "OPEN",
  currentStage: "CONSULTATION",
  arrivedAt: "2026-10-24T01:00:00Z",
  queuePosition: null,
  currentRoom: "Phòng khám 2",
  queueNumber: 12,
  queueStage: "EXAM",
  examQueue: queue(),
  peopleAhead: 3,
  beingCalled: false,
  serviceOrders: [],
  ...patch,
});

const order = (
  patch: Partial<VisitProgressOrder> = {},
): VisitProgressOrder => ({
  orderId: "o-1",
  orderCode: "CLS-1",
  serviceName: "MRI cột sống",
  status: "ORDERED",
  paymentStatus: "PENDING_PAYMENT",
  roomId: null,
  roomName: null,
  queue: null,
  ...patch,
});

const summary = (status: VisitSummary["status"]): VisitSummary => ({
  id: "visit-1",
  visitDate: "2026-10-24",
  status,
  patient: { id: "p", fullName: "Nguyễn Văn An", relationship: null },
});

describe("VisitProgressView", () => {
  it("đang chờ khám: ô việc cần làm báo số thứ tự, phòng và còn bao nhiêu người trước", () => {
    render(
      <VisitProgressView
        progress={progress()}
        summary={summary("WAITING_FOR_EXAMINATION")}
      />,
    );

    expect(screen.getByText("Đang chờ bác sĩ khám")).toBeInTheDocument();
    expect(screen.getAllByText(/Còn 3 người trước bạn/).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText("12")[0]).toHaveClass("tabular-nums");
    expect(screen.getAllByText(/Phòng khám 2/).length).toBeGreaterThan(0);
  });

  it("đang được gọi thì ghi Đang gọi bạn; không còn ai trước thì báo người tiếp theo", () => {
    const { rerender } = render(
      <VisitProgressView
        progress={progress({
          examQueue: queue({ beingCalled: true, peopleAhead: 0 }),
        })}
        summary={summary("WAITING_FOR_EXAMINATION")}
      />,
    );
    expect(screen.getByText("Đang gọi bạn")).toBeInTheDocument();

    rerender(
      <VisitProgressView
        progress={progress({ examQueue: queue({ peopleAhead: 0 }) })}
        summary={summary("WAITING_FOR_EXAMINATION")}
      />,
    );
    expect(
      screen.getAllByText(/Bạn là người tiếp theo/).length,
    ).toBeGreaterThan(0);
  });

  it("chưa vào hàng và không có chỉ định, không biết trạng thái: không vẽ lộ trình", () => {
    render(<VisitProgressView progress={progress({ examQueue: null })} />);

    expect(screen.queryByRole("list")).not.toBeInTheDocument();
    expect(
      screen.getByText("Hiện bạn không chờ ở phòng nào."),
    ).toBeInTheDocument();
  });

  it("một lộ trình gồm khám bệnh, từng chỉ định và bác sĩ kết luận; mỗi chỉ định có số riêng", () => {
    render(
      <VisitProgressView
        progress={progress({
          examQueue: null,
          serviceOrders: [
            order({
              paymentStatus: "PAID",
              roomName: "Phòng xét nghiệm",
              queue: queue({ queueNumber: 4, peopleAhead: 1 }),
            }),
            order({
              orderId: "o-2",
              serviceName: "X-quang",
              paymentStatus: "PAID",
            }),
          ],
        })}
        summary={summary("AWAITING_CONCLUSION")}
      />,
    );

    const steps = within(
      screen.getByRole("list", { name: "Lộ trình lượt khám" }),
    ).getAllByRole("listitem");
    expect(steps).toHaveLength(4);
    expect(steps[0]).toHaveTextContent("Khám bệnh");
    expect(steps[0]).toHaveTextContent("Đã khám xong.");
    expect(steps[1]).toHaveTextContent("MRI cột sống");
    expect(steps[1]).toHaveTextContent("Phòng xét nghiệm");
    expect(steps[1]).toHaveTextContent("Còn 1 người trước bạn.");
    expect(within(steps[1]).getByText("4")).toHaveClass("tabular-nums");
    expect(steps[2]).toHaveTextContent("Chưa có số thứ tự ở phòng thực hiện.");
    expect(steps[3]).toHaveTextContent("Bác sĩ kết luận");
    expect(screen.getByRole("listitem", { current: "step" })).toHaveTextContent(
      "MRI cột sống",
    );
  });

  it("chỉ định chưa thanh toán: vẫn hiện, có nhãn Chưa thanh toán và lối tới trang thanh toán", () => {
    render(
      <VisitProgressView
        progress={progress({ examQueue: null, serviceOrders: [order()] })}
        summary={summary("AWAITING_CONCLUSION")}
      />,
    );

    expect(screen.getByText("Cần thanh toán 1 chỉ định")).toBeInTheDocument();
    expect(screen.getAllByText("Chưa thanh toán").length).toBeGreaterThan(0);
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
          examQueue: null,
          serviceOrders: [order({ status: "CANCELLED" })],
        })}
        summary={summary("IN_EXAMINATION")}
      />,
    );

    expect(
      screen.queryByRole("link", {
        name: "Thanh toán các chỉ định chưa thanh toán",
      }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Đã hủy.")).toBeInTheDocument();
  });

  it("có kết quả sẵn sàng thì mở khu kết quả; khi chưa có thì ghi chú đợi bác sĩ kết luận", () => {
    const { rerender } = render(
      <VisitProgressView
        progress={progress({
          examQueue: null,
          serviceOrders: [
            order({ status: "RESULTS_AVAILABLE", paymentStatus: "PAID" }),
          ],
        })}
        summary={summary("AWAITING_CONCLUSION")}
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
