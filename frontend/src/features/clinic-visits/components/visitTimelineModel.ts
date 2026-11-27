import type { VisitProgress, VisitSummary } from "../types/visitProgress";
import { queueAheadText } from "./visitPresentation";

// Lộ trình của một lượt khám gộp thành MỘT danh sách: Khám bệnh → từng chỉ định → Bác sĩ kết luận, cùng một
// ô "việc cần làm bây giờ" chọn tự động theo tình huống. Hàng khám và hàng của từng chỉ định lấy từ dữ liệu
// riêng (examQueue, order.queue) nên không lẫn nhau; chỉ định luôn có dòng của nó dù thiếu dữ liệu hàng chờ.
// Chỉ có luật hiển thị, không có luật nghiệp vụ.

export type StepState = "done" | "active" | "called" | "todo" | "cancelled";

export interface TimelineStep {
  key: string;
  title: string;
  state: StepState;
  /** Phòng của bước (nếu biết). */
  room: string | null;
  /** Mã chỉ định; null với bước khám và kết luận. */
  code: string | null;
  /** Số thứ tự khi đang xếp hàng. */
  number: number | null;
  /** Số người đứng trước, dùng chọn bước nên làm trước. */
  ahead: number | null;
  /** Câu nói trạng thái của bước. */
  text: string | null;
  /** Nhãn nhấn khi cần hành động hoặc đã có kết quả. */
  flag: { label: string; tone: "warning" | "success" } | null;
}

export interface NextAction {
  kind: "called" | "waiting" | "warning" | "info" | "done";
  title: string;
  detail: string | null;
  number: number | null;
}

export interface VisitTimeline {
  steps: TimelineStep[];
  next: NextAction | null;
}

const CALLED_SUFFIX = ". Vui lòng vào phòng ngay.";

export function buildVisitTimeline(
  progress: VisitProgress,
  summary: VisitSummary | null,
): VisitTimeline {
  const status = summary?.status ?? null;
  const orders = progress.serviceOrders;
  const live = orders.filter((order) => order.status !== "CANCELLED");
  const pending = live.filter((order) => order.status === "ORDERED");
  const unpaid = live.filter(
    (order) => order.paymentStatus === "PENDING_PAYMENT",
  );
  const exam = progress.examQueue;
  const examNumber = exam?.queueNumber ?? null;

  if (status === "INCOMPLETE" || status === "CANCELLED_LEFT")
    return { steps: [], next: null };
  if (!status && examNumber === null && orders.length === 0)
    return { steps: [], next: null };

  const steps: TimelineStep[] = [];

  // Bước khám bệnh.
  const examDone =
    status === "AWAITING_CONCLUSION" ||
    status === "COMPLETED" ||
    (status === null && examNumber === null && orders.length > 0);
  const examStep = (patch: Partial<TimelineStep>): TimelineStep => ({
    key: "exam",
    title: "Khám bệnh",
    state: "todo",
    room: null,
    code: null,
    number: null,
    ahead: null,
    text: null,
    flag: null,
    ...patch,
  });
  if (exam && examNumber !== null) {
    steps.push(
      examStep({
        state: exam.beingCalled ? "called" : "active",
        room: exam.roomCode,
        number: examNumber,
        ahead: exam.peopleAhead,
        text: queueAheadText(exam) + (exam.beingCalled ? CALLED_SUFFIX : ""),
      }),
    );
  } else if (examDone) {
    steps.push(examStep({ state: "done", text: "Đã khám xong." }));
  } else if (status === "IN_EXAMINATION") {
    steps.push(
      examStep({ state: "active", text: "Bác sĩ đang khám cho bạn." }),
    );
  } else {
    steps.push(
      examStep({
        state: "active",
        text: "Chưa có số thứ tự khám. Nếu bạn đã đến phòng khám, hãy báo lễ tân.",
      }),
    );
  }

  // Mỗi chỉ định một bước, tự mang số thứ tự và lý do nếu chưa có số.
  for (const order of orders) {
    const base: TimelineStep = {
      key: order.orderId,
      title: order.serviceName,
      state: "todo",
      room: order.roomName ?? order.queue?.roomCode ?? null,
      code: order.orderCode,
      number: null,
      ahead: null,
      text: null,
      flag: null,
    };
    const queue = order.queue;
    if (order.status === "CANCELLED") {
      steps.push({ ...base, state: "cancelled", text: "Đã hủy." });
    } else if (order.status === "RESULTS_AVAILABLE") {
      steps.push({
        ...base,
        state: "done",
        text: "Đã có kết quả.",
        flag: { label: "Có kết quả", tone: "success" },
      });
    } else if (queue && queue.queueNumber !== null) {
      steps.push({
        ...base,
        state: queue.beingCalled ? "called" : "active",
        number: queue.queueNumber,
        ahead: queue.peopleAhead,
        text: queueAheadText(queue) + (queue.beingCalled ? CALLED_SUFFIX : ""),
      });
    } else if (order.paymentStatus === "PENDING_PAYMENT") {
      steps.push({
        ...base,
        text: "Cần thanh toán trước khi được xếp số.",
        flag: { label: "Chưa thanh toán", tone: "warning" },
      });
    } else {
      steps.push({ ...base, text: "Chưa có số thứ tự ở phòng thực hiện." });
    }
  }

  // Bước bác sĩ kết luận.
  const concluding = status === "AWAITING_CONCLUSION" && pending.length === 0;
  steps.push({
    key: "conclusion",
    title: "Bác sĩ kết luận",
    state: status === "COMPLETED" ? "done" : concluding ? "active" : "todo",
    room: null,
    code: null,
    number: null,
    ahead: null,
    text:
      status === "COMPLETED"
        ? "Đã kết luận."
        : concluding
          ? "Bác sĩ đang xem kết quả để kết luận."
          : live.length > 0
            ? "Sau khi có đủ kết quả chỉ định."
            : "Sau khi bác sĩ khám xong.",
    flag: null,
  });

  return {
    steps,
    next: chooseNextAction(steps, {
      status,
      pending: pending.length,
      unpaid: unpaid.length,
    }),
  };
}

/** Chọn một việc nên làm ngay: được gọi > đang chờ (ít người trước nhất) > cần thanh toán > đang diễn ra > xong. */
function chooseNextAction(
  steps: TimelineStep[],
  ctx: { status: string | null; pending: number; unpaid: number },
): NextAction | null {
  const called = steps.find((step) => step.state === "called");
  if (called) {
    return {
      kind: "called",
      title: "Đang gọi bạn",
      detail: `Vui lòng vào ${called.room ?? "phòng"} ngay${called.key === "exam" ? "" : ` để ${called.title}`}.`,
      number: called.number,
    };
  }

  const waiting = steps
    .filter((step) => step.state === "active" && step.number !== null)
    .sort((a, b) => (a.ahead ?? 0) - (b.ahead ?? 0));
  const [first] = waiting;
  if (first) {
    const others = waiting.length - 1;
    return {
      kind: "waiting",
      title:
        first.key === "exam"
          ? "Đang chờ bác sĩ khám"
          : `Đang chờ ${first.title}`,
      detail: [
        first.text,
        first.room,
        others > 0 ? `Còn ${others} chỉ định khác cũng đang chờ.` : null,
      ]
        .filter(Boolean)
        .join(" · "),
      number: first.number,
    };
  }

  if (ctx.unpaid > 0) {
    return {
      kind: "warning",
      title: `Cần thanh toán ${ctx.unpaid} chỉ định`,
      detail: "Thanh toán xong, bạn sẽ được xếp số để làm chỉ định.",
      number: null,
    };
  }
  if (ctx.status === "COMPLETED") {
    return {
      kind: "done",
      title: "Lượt khám đã hoàn tất",
      detail: "Kết quả có ở mục Kết quả khám.",
      number: null,
    };
  }
  if (ctx.status === "IN_EXAMINATION")
    return {
      kind: "info",
      title: "Bác sĩ đang khám cho bạn",
      detail: null,
      number: null,
    };
  if (ctx.status === "AWAITING_CONCLUSION") {
    return ctx.pending > 0
      ? {
          kind: "info",
          title: "Hãy đến phòng thực hiện chỉ định",
          detail: "Chưa có số thứ tự thì hỏi lễ tân để được xếp hàng.",
          number: null,
        }
      : {
          kind: "info",
          title: "Bác sĩ đang xem kết quả",
          detail: "Bạn chưa cần làm gì thêm. Bác sĩ sẽ kết luận.",
          number: null,
        };
  }
  return {
    kind: "info",
    title: "Chưa có số thứ tự",
    detail: "Nếu bạn đã đến phòng khám, hãy báo lễ tân.",
    number: null,
  };
}
