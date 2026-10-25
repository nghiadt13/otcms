"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useImaging } from "../ImagingProvider";
import { CURRENT_ROOM, statusLabels, visitLabels } from "../demoData";
import {
  ActionLink,
  Badge,
  Empty,
  Icon,
  Info,
  Modal,
  OrderBadge,
  PageHeading,
  Pagination,
  Panel,
  SectionTabs,
  PatientAvatar,
} from "./ImagingUi";
import type { ImagingOrder } from "../types";

export function OrderListScreen({
  statusMode = false,
}: {
  statusMode?: boolean;
}) {
  const { state } = useImaging();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [service, setService] = useState("all");
  const [page, setPage] = useState(1);
  const own = state.orders.filter(
    (o) => o.room === CURRENT_ROOM && o.kind === "imaging",
  );
  const matches = own.filter(
    (o) =>
      `${o.id} ${o.patient} ${o.patientId}`
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (status === "all" || o.status === status) &&
      (service === "all" || o.service === service),
  );
  const current = Math.min(page, Math.max(1, Math.ceil(matches.length / 5)));
  return (
    <>
      <PageHeading
        eyebrow="CHẨN ĐOÁN HÌNH ẢNH"
        title={
          statusMode ? "Trạng thái chỉ định" : "Chỉ định chẩn đoán hình ảnh"
        }
        description={
          statusMode
            ? "Theo dõi từng chỉ định của phòng từ thanh toán đến khi có kết quả."
            : "Tra cứu chỉ định theo mã hoặc tên bệnh nhân trước khi thực hiện dịch vụ."
        }
      />
      <div className="im-summary-strip">
        {Object.entries(statusLabels).map(([value, label]) => (
          <button
            key={value}
            className={status === value ? "im-summary-active" : ""}
            onClick={() => {
              setStatus(status === value ? "all" : value);
              setPage(1);
            }}
          >
            <span>{label}</span>
            <strong>
              {own
                .filter((o) => o.status === value)
                .length.toString()
                .padStart(2, "0")}
            </strong>
          </button>
        ))}
      </div>
      <Panel
        title={
          statusMode ? "Theo dõi chỉ định của phòng" : "Danh sách chỉ định"
        }
        subtitle="Phòng X-quang 01 · XR-01"
      >
        <div className="im-toolbar im-toolbar-wrap">
          <label className="im-search">
            <Icon name="search" />
            <input
              aria-label="Tìm chỉ định"
              placeholder="Mã chỉ định, tên hoặc mã bệnh nhân…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
            />
          </label>
          <label className="im-filter">
            <span>Trạng thái</span>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">Tất cả trạng thái</option>
              {Object.entries(statusLabels).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          </label>
          <label className="im-filter">
            <span>Dịch vụ</span>
            <select
              value={service}
              onChange={(e) => {
                setService(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">Tất cả dịch vụ</option>
              {[...new Set(own.map((o) => o.service))].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          {(search || status !== "all" || service !== "all") && (
            <button
              className="im-text-button"
              onClick={() => {
                setSearch("");
                setStatus("all");
                setService("all");
                setPage(1);
              }}
            >
              Xóa bộ lọc
            </button>
          )}
        </div>
        <div className="im-table-scroll">
          <table className="im-table">
            <thead>
              <tr>
                <th>Mã chỉ định</th>
                <th>Bệnh nhân</th>
                <th>Dịch vụ</th>
                <th>{statusMode ? "Thời điểm" : "Bác sĩ yêu cầu"}</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {matches.slice((current - 1) * 5, current * 5).map((o) => (
                <tr key={o.id}>
                  <td>
                    <Link
                      className="im-id-link"
                      href={`/imaging/orders/${o.id}`}
                    >
                      {o.id}
                    </Link>
                    <small>
                      {o.visitId} · Đợt {o.round}
                    </small>
                  </td>
                  <td>
                    <div className="im-patient-cell">
                      <PatientAvatar name={o.patient} />
                      <div>
                        <strong>{o.patient}</strong>
                        <small>
                          {o.patientId} · {o.age} tuổi · {o.sex}
                        </small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <strong>{o.service}</strong>
                    <small>{o.room}</small>
                  </td>
                  <td>
                    {statusMode ? (
                      <>
                        <span>Tạo: {o.createdAt}</span>
                        <small>
                          {o.resultAt
                            ? `Có kết quả: ${o.resultAt}`
                            : o.paidAt
                              ? `Thanh toán: ${o.paidAt}`
                              : "Chưa thanh toán"}
                        </small>
                      </>
                    ) : (
                      o.doctor
                    )}
                  </td>
                  <td>
                    <OrderBadge status={o.status} />
                  </td>
                  <td>
                    <Link
                      className="im-text-button"
                      href={`/imaging/orders/${o.id}`}
                    >
                      Chi tiết <Icon name="arrow" size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!matches.length && <Empty />}
        <Pagination page={current} total={matches.length} onChange={setPage} />
      </Panel>
    </>
  );
}

export function PatientHeader({ order }: { order: ImagingOrder }) {
  return (
    <div className="im-patient-header">
      <PatientAvatar name={order.patient} />
      <div>
        <h2>{order.patient}</h2>
        <p>
          {order.patientId} <span>·</span> {order.age} tuổi <span>·</span>{" "}
          {order.sex}
        </p>
      </div>
      <div className="im-patient-header-end">
        <span>Lượt khám</span>
        <strong>{order.visitId}</strong>
      </div>
    </div>
  );
}
export function OrderDetailsScreen({ id }: { id: string }) {
  const { state, update, notify } = useImaging();
  const [issueOpen, setIssueOpen] = useState(false);
  const [section, setSection] = useState("details");
  const order = state.orders.find(
    (o) => o.id === id && o.room === CURRENT_ROOM && o.kind === "imaging",
  );
  if (!order)
    return (
      <Empty
        title="Không thể mở chỉ định"
        description="Chỉ định không tồn tại hoặc không thuộc phòng được phân công."
      >
        <ActionLink href="/imaging/orders">Quay lại danh sách</ActionLink>
      </Empty>
    );
  const visit = state.visits.find((v) => v.id === order.visitId);
  const versions = state.studies[id] || [];
  const canPerform = order.status === "Paid";
  const canReplace = order.status === "Results Available";
  function recordIssue(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const reason = String(data.get("reason") || "").trim();
    const kind = String(data.get("kind") || "");
    if (!reason) return;
    update((s) => ({
      ...s,
      orders: s.orders.map((o) =>
        o.id === id ? { ...o, issue: { kind, reason, at: "09:30" } } : o,
      ),
      queue: s.queue.map((q) =>
        q.visitId === order!.visitId &&
        q.room === CURRENT_ROOM &&
        q.status === "Being Served"
          ? { ...q, status: "Closed" }
          : q,
      ),
      activity: [
        {
          id: `EV-${Date.now()}`,
          orderId: id,
          text: `Ghi nhận ${kind.toLowerCase()}: ${reason}. Chỉ định giữ nguyên, chờ xử lý hoặc bệnh nhân quay lại.`,
          at: "09:30",
        },
        ...s.activity,
      ],
    }));
    setIssueOpen(false);
    notify(
      "Đã ghi nhận lý do trong bản xem thử. Chỉ định không bị hủy và khoản thanh toán được giữ nguyên.",
    );
  }
  return (
    <>
      <Link className="im-back-link" href="/imaging/orders">
        <Icon name="back" size={16} />
        Danh sách chỉ định
      </Link>
      <PageHeading
        eyebrow={`CHỈ ĐỊNH ${order.id}`}
        title="Chi tiết chỉ định hình ảnh"
        description="Đối chiếu thông tin bệnh nhân và yêu cầu của bác sĩ trước khi thực hiện."
      >
        {canPerform && (
          <ActionLink href={`/imaging/orders/${id}/upload`}>
            <Icon name="upload" />
            Tải và gửi DICOM
          </ActionLink>
        )}
        {canReplace && (
          <ActionLink href={`/imaging/orders/${id}/upload`} secondary>
            <Icon name="refresh" />
            Thay thế study
          </ActionLink>
        )}
      </PageHeading>
      <PatientHeader order={order} />
      {order.status === "Pending Payment" && (
        <Info tone="amber">
          <strong>Chỉ định này chưa được thanh toán.</strong>
          <p>Vui lòng hướng dẫn bệnh nhân thanh toán trước khi thực hiện.</p>
        </Info>
      )}
      {order.status === "Cancelled" && (
        <Info tone="red">
          <strong>Chỉ định đã hủy</strong>
          <p>{order.cancellationReason}</p>
        </Info>
      )}
      {order.issue && (
        <details className="im-help">
          <summary>Đã ghi nhận: {order.issue.kind}</summary>
          <Info tone="amber">
            <strong>{order.issue.kind}</strong>
            <p>
              {order.issue.reason} · Ghi nhận lúc {order.issue.at}. Chỉ định vẫn
              giữ nguyên trạng thái.
            </p>
          </Info>
        </details>
      )}
      <SectionTabs
        value={section}
        onChange={setSection}
        items={[
          { id: "details", label: "Thông tin chỉ định" },
          { id: "study", label: `Study & lịch sử (${versions.length})` },
        ]}
      />
      {section === "details" && (
        <>
          <div className="im-two-column">
            <Panel
              title="Thông tin chỉ định"
              action={<OrderBadge status={order.status} />}
            >
              <dl className="im-details">
                <div>
                  <dt>Mã chỉ định</dt>
                  <dd>{id}</dd>
                </div>
                <div>
                  <dt>Dịch vụ</dt>
                  <dd>{order.service}</dd>
                </div>
                <div>
                  <dt>Phòng thực hiện</dt>
                  <dd>Phòng X-quang 01 · {order.room}</dd>
                </div>
                <div>
                  <dt>Bác sĩ yêu cầu</dt>
                  <dd>{order.doctor}</dd>
                </div>
                <div>
                  <dt>Vùng cơ thể</dt>
                  <dd>{order.bodyArea}</dd>
                </div>
                <div>
                  <dt>Đợt chỉ định</dt>
                  <dd>Đợt {order.round}</dd>
                </div>
                <div>
                  <dt>Thời điểm tạo</dt>
                  <dd>24/10/2026 · {order.createdAt}</dd>
                </div>
              </dl>
              <div className="im-detail-block">
                <h3>Lý do lâm sàng</h3>
                <p>{order.reason}</p>
                <h3>Hướng dẫn thực hiện</h3>
                <p>{order.instructions}</p>
              </div>
            </Panel>
            <div className="im-stack">
              <Panel title="Tiến trình chỉ định">
                <ol className="im-timeline">
                  {[
                    {
                      label: "Bác sĩ tạo chỉ định",
                      text: order.createdAt,
                      done: true,
                    },
                    {
                      label: "Thanh toán dịch vụ",
                      text: order.paidAt || "Chờ bệnh nhân thanh toán",
                      done: !!order.paidAt,
                    },
                    {
                      label: "Study có kết quả",
                      text: order.resultAt || "Chưa gửi study",
                      done: order.status === "Results Available",
                    },
                  ].map((s, i) => (
                    <li key={s.label} className={s.done ? "im-step-done" : ""}>
                      <span>
                        {s.done ? <Icon name="check" size={14} /> : i + 1}
                      </span>
                      <div>
                        <strong>{s.label}</strong>
                        <p>{s.text}</p>
                      </div>
                    </li>
                  ))}
                </ol>
              </Panel>
              <Panel title="Lượt khám liên quan">
                <div className="im-panel-body">
                  <div className="im-inline-between">
                    <strong>{order.visitId}</strong>
                    <Badge>{visit ? visitLabels[visit.status] : "—"}</Badge>
                  </div>
                  <p className="im-muted">Bác sĩ phụ trách: {visit?.doctor}</p>
                  <ActionLink
                    secondary
                    href={`/imaging/visits/${order.visitId}`}
                  >
                    Xem tiến trình lượt khám <Icon name="arrow" size={16} />
                  </ActionLink>
                </div>
              </Panel>
            </div>
          </div>
        </>
      )}
      {section === "study" && (
        <div className="im-history-content">
          {versions.length > 0 && (
            <Panel
              title="Study đã gửi"
              subtitle="Giữ lại phiên bản trước khi có thay thế"
            >
              <div className="im-table-scroll">
                <table className="im-table">
                  <thead>
                    <tr>
                      <th>Phiên bản</th>
                      <th>Study Instance UID</th>
                      <th>Series / file</th>
                      <th>Gửi lúc</th>
                      <th>Lý do thay thế</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...versions].reverse().map((v) => (
                      <tr key={v.version}>
                        <td>
                          <Badge tone={v.replaced ? "neutral" : "green"}>
                            v{v.version} ·{" "}
                            {v.replaced ? "Đã thay thế" : "Hiện hành"}
                          </Badge>
                        </td>
                        <td className="im-uid">{v.uid}</td>
                        <td>
                          {v.series} series · {v.files} file
                        </td>
                        <td>{v.uploadedAt}</td>
                        <td>{v.reason || "Gửi lần đầu"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {visit?.status === "Completed" && (
                <div className="im-panel-body im-muted">
                  Phiếu kết quả hiện hành: phiên bản {visit.sheetVersion}. Nội
                  dung nhận định do bác sĩ phụ trách ghi nhận.
                </div>
              )}
            </Panel>
          )}
          {state.activity.some((a) => a.orderId === id) && (
            <Panel title="Lịch sử xử lý chỉ định">
              <div className="im-panel-body im-stack">
                {state.activity
                  .filter((a) => a.orderId === id)
                  .map((a) => (
                    <p key={a.id} className="im-small">
                      <strong>{a.at}</strong> · {a.text}
                    </p>
                  ))}
              </div>
            </Panel>
          )}
          {versions.length === 0 && (
            <Panel>
              <Empty
                title="Chưa có study đã gửi"
                description="Study sẽ hiển thị tại đây sau khi được gửi thành công."
              />
            </Panel>
          )}
        </div>
      )}
      {canPerform && (
        <div className="im-action-footer">
          <span className="im-muted">
            Chỉ thực hiện khi đã đối chiếu đúng bệnh nhân.
          </span>
          <button
            className="im-button im-button-secondary"
            onClick={() => setIssueOpen(true)}
          >
            <Icon name="alert" />
            Không thể thực hiện
          </button>
        </div>
      )}
      {issueOpen && (
        <Modal
          title="Ghi nhận không thể thực hiện"
          close={() => setIssueOpen(false)}
        >
          <form onSubmit={recordIssue}>
            <label className="im-field">
              Trường hợp
              <select name="kind">
                <option>Chống chỉ định</option>
                <option>Bệnh nhân từ chối</option>
                <option>Thiết bị chưa sẵn sàng</option>
                <option>Cần bệnh nhân quay lại</option>
              </select>
            </label>
            <label className="im-field">
              Lý do cụ thể
              <textarea
                name="reason"
                required
                maxLength={1000}
                placeholder="Ghi rõ tình trạng và lý do chưa thực hiện được dịch vụ…"
              />
            </label>
            <Info>
              Với chống chỉ định hoặc từ chối, bác sĩ yêu cầu quyết định hủy
              hoặc thay chỉ định. Với dịch vụ cần thực hiện sau, chỉ định đã
              thanh toán vẫn có hiệu lực trong thời hạn lượt khám.
            </Info>
            <footer>
              <button
                type="button"
                className="im-button im-button-secondary"
                onClick={() => setIssueOpen(false)}
              >
                Quay lại
              </button>
              <button className="im-button" type="submit">
                Lưu lý do
              </button>
            </footer>
          </form>
        </Modal>
      )}
    </>
  );
}
