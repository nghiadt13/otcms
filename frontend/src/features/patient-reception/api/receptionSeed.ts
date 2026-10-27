import { addDays, clinicDate, clinicTimestamp } from "../schema/receptionRules";
import type { ReceptionState, Patient } from "../types";

/** Synthetic records. Runtime uses real clinic time; an explicit fixture date keeps tests deterministic. */
export function createReceptionSeed(fixtureDay?: string): ReceptionState {
  const now = new Date();
  const day = fixtureDay ?? clinicDate(now);
  const names = [
    "Nguyễn Minh Anh",
    "Trần Văn Bình",
    "Lê Thị Hạnh",
    "Phạm Gia Bảo",
    "Hoàng Đức Long",
    "Đỗ Thu Hà",
    "Vũ Văn Nam",
    "Bùi Thanh Mai",
    "Nguyễn An Nhiên",
  ];
  const patients: Patient[] = names.map((name, i) => ({
    id: `BN${String(i + 1).padStart(4, "0")}`,
    name,
    dob: [
      "1992-04-15",
      "1948-08-12",
      "1985-10-07",
      "2022-02-20",
      "1996-06-30",
      "1980-11-02",
      "1975-03-09",
      "1990-09-18",
      "2018-01-05",
    ][i],
    phone: `090000000${i + 1}`,
    sex: [0, 2, 5, 7, 8].includes(i) ? "Nữ" : "Nam",
    address: "Thái Nguyên",
    nationalId: "",
    email: i === 8 ? "" : `benhnhan${i + 1}@example.com`,
  }));
  const clock = fixtureDay ? `${day}T09:10:00+07:00` : clinicTimestamp(now);
  const state: ReceptionState = {
    version: 1,
    clock,
    sequence: 100,
    patients,
    account: {
      id: "reception-quang",
      name: "Thiên Quang",
      username: "thienquang",
      phone: "0900000099",
      email: "letan@example.com",
      avatar: null,
    },
    doctors: [
      {
        id: "bs-an",
        name: "BS. Nguyễn Hoài An",
        roomId: "p101",
        absent: false,
      },
      {
        id: "bs-minh",
        name: "BS. Trần Quang Minh",
        roomId: "p102",
        absent: false,
      },
      { id: "bs-lan", name: "BS. Lê Ngọc Lan", roomId: "p103", absent: true },
    ],
    rooms: [
      {
        id: "p101",
        name: "P.101 · Khám chỉnh hình",
        category: "exam",
        available: true,
        serviceIds: ["kham"],
      },
      {
        id: "p102",
        name: "P.102 · Khám chỉnh hình",
        category: "exam",
        available: true,
        serviceIds: ["kham"],
      },
      {
        id: "p103",
        name: "P.103 · Khám chỉnh hình",
        category: "exam",
        available: true,
        serviceIds: ["kham"],
      },
      {
        id: "xq1",
        name: "P.201 · X-quang",
        category: "imaging",
        available: true,
        serviceIds: ["xquang"],
      },
      {
        id: "xq2",
        name: "P.202 · X-quang",
        category: "imaging",
        available: true,
        serviceIds: ["xquang"],
      },
      {
        id: "sa",
        name: "P.203 · Siêu âm",
        category: "imaging",
        available: false,
        serviceIds: ["sieuam"],
      },
      {
        id: "xn",
        name: "P.301 · Xét nghiệm",
        category: "lab",
        available: true,
        serviceIds: ["mau", "crp"],
      },
    ],
    services: [
      {
        id: "kham",
        name: "Khám chuyên khoa chấn thương chỉnh hình",
        category: "exam",
        price: 200000,
        active: true,
      },
      {
        id: "xquang",
        name: "X-quang khớp gối thẳng, nghiêng",
        category: "imaging",
        price: 180000,
        active: true,
      },
      {
        id: "sieuam",
        name: "Siêu âm phần mềm cơ xương khớp",
        category: "imaging",
        price: 150000,
        active: true,
      },
      {
        id: "mau",
        name: "Tổng phân tích tế bào máu",
        category: "lab",
        price: 100000,
        active: true,
      },
      {
        id: "crp",
        name: "Định lượng CRP",
        category: "lab",
        price: 90000,
        active: true,
      },
    ],
    shifts: [],
    appointments: [
      {
        id: "LH001",
        patientId: "BN0001",
        doctorId: "bs-an",
        serviceId: "kham",
        date: day,
        time: "09:20",
        status: "booked",
      },
      {
        id: "LH002",
        patientId: "BN0002",
        doctorId: "bs-minh",
        serviceId: "kham",
        date: day,
        time: "09:20",
        status: "booked",
      },
      {
        id: "LH003",
        patientId: "BN0003",
        doctorId: "bs-lan",
        serviceId: "kham",
        date: day,
        time: "10:00",
        status: "booked",
      },
      {
        id: "LH004",
        patientId: "BN0008",
        doctorId: "bs-an",
        serviceId: "kham",
        date: addDays(day, 1),
        time: "09:00",
        status: "booked",
      },
      {
        id: "LH005",
        patientId: "BN0009",
        doctorId: "bs-minh",
        serviceId: "kham",
        date: day,
        time: "08:00",
        status: "cancelled",
      },
    ],
    visits: [
      {
        id: "LK001",
        patientId: "BN0004",
        doctorId: "bs-an",
        firstCheckIn: day,
        status: "waiting",
        priority: "child",
        followUp: false,
      },
      {
        id: "LK002",
        patientId: "BN0005",
        doctorId: "bs-an",
        firstCheckIn: day,
        status: "waiting",
        priority: "normal",
        followUp: false,
      },
      {
        id: "LK003",
        patientId: "BN0006",
        doctorId: "bs-minh",
        firstCheckIn: addDays(day, -2),
        status: "awaiting",
        priority: "normal",
        followUp: false,
      },
      {
        id: "LK004",
        patientId: "BN0007",
        doctorId: "bs-minh",
        firstCheckIn: day,
        status: "awaiting",
        priority: "normal",
        followUp: false,
      },
      {
        id: "LK005",
        patientId: "BN0008",
        doctorId: "bs-an",
        firstCheckIn: addDays(day, -1),
        status: "completed",
        priority: "normal",
        followUp: false,
      },
      {
        id: "LK006",
        patientId: "BN0009",
        doctorId: "bs-lan",
        firstCheckIn: addDays(day, -8),
        status: "incomplete",
        priority: "normal",
        followUp: false,
      },
    ],
    queues: [
      {
        id: "q1",
        visitId: "LK001",
        roomId: "p101",
        kind: "exam",
        number: "101-001",
        joinedAt: `${day}T08:50:00+07:00`,
        date: day,
        status: "waiting",
        priority: "child",
        position: 1,
      },
      {
        id: "q2",
        visitId: "LK002",
        roomId: "p101",
        kind: "exam",
        number: "101-002",
        joinedAt: `${day}T09:00:00+07:00`,
        date: day,
        status: "waiting",
        priority: "normal",
        position: 2,
      },
      {
        id: "q3",
        visitId: "LK004",
        roomId: "p102",
        kind: "review",
        number: "102-001",
        joinedAt: `${day}T09:05:00+07:00`,
        date: day,
        status: "waiting",
        priority: "normal",
        position: 1,
      },
    ],
    orders: [
      {
        id: "CD001",
        visitId: "LK003",
        serviceId: "xquang",
        roomId: "xq1",
        round: 1,
        status: "pending",
      },
      {
        id: "CD002",
        visitId: "LK003",
        serviceId: "mau",
        roomId: "xn",
        round: 1,
        status: "pending",
      },
      {
        id: "CD003",
        visitId: "LK004",
        serviceId: "xquang",
        roomId: "xq1",
        round: 1,
        status: "results",
        resultAt: `${day}T09:05:00+07:00`,
      },
      {
        id: "CD004",
        visitId: "LK004",
        serviceId: "crp",
        roomId: "xn",
        round: 1,
        status: "results",
        resultAt: `${day}T09:00:00+07:00`,
      },
    ],
    charges: [],
    payments: [],
    transactions: [],
    careLinks: [
      {
        id: "link1",
        patientId: "BN0009",
        accountName: "Nguyễn Thu Thủy",
        accountEmail: "nguoichamsoc@example.com",
        relationship: "Mẹ",
        status: "pending",
      },
    ],
    notices: [
      {
        id: "tb1",
        title: "Lịch hẹn cần đổi bác sĩ",
        body: "BS. Lê Ngọc Lan vắng ca. Lịch LH003 lúc 10:00 đang chờ sắp xếp bác sĩ khác cùng giờ.",
        read: false,
        href: "/reception/appointments",
        createdAt: clock,
      },
      {
        id: "tb2",
        title: "Đã đủ kết quả cận lâm sàng",
        body: "Lượt LK004 của Vũ Văn Nam đã có đủ kết quả và đang chờ đọc kết quả tại P.102.",
        read: false,
        href: "/reception/queues",
        createdAt: clock,
      },
      {
        id: "tb3",
        title: "Ca làm việc của bạn",
        body: "Ca sáng 07:00–12:00 tại quầy tiếp nhận.",
        read: true,
        href: "/reception/schedule",
        createdAt: clock,
      },
    ],
    resultSheets: [
      {
        id: "KQ005",
        visitId: "LK005",
        version: 1,
        replaced: false,
        issuedAt: `${addDays(day, -1)}T11:00:00+07:00`,
        diagnosis: "S93.4 · Bong gân cổ chân",
        conclusion: "Đã hoàn thành khám và đánh giá.",
        advice:
          "Thực hiện hướng dẫn chăm sóc và tái khám theo chỉ định đã ghi nhận của bác sĩ. Đây là dữ liệu mẫu để kiểm tra giao diện.",
        doctorName: "BS. Nguyễn Hoài An",
      },
    ],
    audit: [],
  };
  for (let i = 0; i < 14; i++)
    for (const staffId of [...state.doctors.map((d) => d.id), state.account.id])
      for (const [start, end] of [
        ["07:00", "12:00"],
        ["13:00", "17:00"],
      ])
        state.shifts.push({
          id: `${staffId}-${i}-${start}`,
          staffId,
          date: addDays(day, i),
          start,
          end,
          roomId:
            state.doctors.find((d) => d.id === staffId)?.roomId ?? "reception",
        });
  for (const a of state.appointments)
    state.charges.push({
      id: `phi-${a.id}`,
      patientId: a.patientId,
      contextId: a.id,
      serviceId: a.serviceId,
      amount: 200000,
      status:
        a.status === "cancelled"
          ? "cancelled"
          : a.id === "LH002"
            ? "paid"
            : "pending",
      createdAt: clock,
      paymentId: a.id === "LH002" ? "PT001" : undefined,
    });
  state.payments.push({
    id: "PT001",
    patientId: "BN0002",
    patientName: patients[1].name,
    contextId: "LH002",
    items: [
      { chargeId: "phi-LH002", name: state.services[0].name, amount: 200000 },
    ],
    amount: 200000,
    method: "vnpay",
    createdAt: clock,
    receivedBy: "VNPay",
    reference: "DEMO-VNP-001",
  });
  for (const order of state.orders) {
    const visit = state.visits.find((v) => v.id === order.visitId)!;
    state.charges.push({
      id: `phi-${order.id}`,
      patientId: visit.patientId,
      contextId: visit.id,
      serviceId: order.serviceId,
      orderId: order.id,
      amount: state.services.find((s) => s.id === order.serviceId)!.price,
      status: order.status === "pending" ? "pending" : "paid",
      createdAt: clock,
    });
  }
  return state;
}
