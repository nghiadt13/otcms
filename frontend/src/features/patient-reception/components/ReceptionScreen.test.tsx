import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReceptionProvider } from "./ReceptionProvider";
import { ReceptionScreen } from "./ReceptionScreen";
import { resetReceptionMock, loadReception } from "../api/receptionApi";
import { createReceptionSeed } from "../api/receptionSeed";

const navigation = vi.hoisted(() => ({ query: "" }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(navigation.query),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
// Hồ sơ bệnh nhân đã nối API thật: chỉ giả lập URL /api/v1/patient-profiles, URL khác đi đường cũ.
vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>();
  const minhAnh = {
    patientProfileId: "BN0001",
    fullName: "Nguyễn Minh Anh",
    phoneCanonical: "0900000001",
    dateOfBirth: "1992-04-15",
    phoneOwnerType: "PATIENT",
    gender: "FEMALE",
    address: "Thái Nguyên",
    nationalId: null,
    identityDocumentType: null,
    version: 1,
  };
  const invoice = {
    id: "inv-1",
    invoiceNumber: "INV-1",
    appointmentId: "a1",
    visitId: null,
    status: "OPEN",
    totalAmount: 200000,
    paidAmount: 0,
    outstandingBalance: 200000,
    issuedAt: "2026-11-17",
    items: [
      {
        id: "item-1",
        lineNumber: 1,
        serviceId: null,
        description: "Phí khám",
        quantity: 1,
        unitPrice: 200000,
        lineTotal: 200000,
        status: "PENDING",
      },
    ],
  };
  const receipt = {
    id: "pay-1",
    receiptNumber: "RC-1",
    invoiceId: "inv-1",
    invoiceNumber: "INV-1",
    patientProfileId: "BN0001",
    patientName: "Nguyễn Minh Anh",
    amount: 200000,
    currency: "VND",
    method: "CASH",
    channel: "COUNTER",
    confirmedAt: "2026-11-17T08:00:00Z",
    receivedByName: null,
    providerTransactionReference: null,
    items: [
      {
        itemId: "item-1",
        lineNumber: 1,
        description: "Phí khám",
        quantity: 1,
        unitPrice: 200000,
        lineTotal: 200000,
        status: "PAID",
        refundReason: null,
      },
    ],
  };
  return {
    ...actual,
    apiRequest: (path: `/api/v1/${string}`, init?: RequestInit) => {
      if (path.startsWith("/api/v1/reception/patients/BN0001/billing?"))
        return Promise.resolve({
          patientProfileId: "BN0001",
          patientName: "Nguyễn Minh Anh",
          invoices: {
            items: [invoice],
            page: 0,
            size: 20,
            totalItems: 1,
            totalPages: 1,
          },
        });
      if (path === "/api/v1/invoices/inv-1/counter-payments") {
        expect(JSON.parse(String(init?.body))).toEqual({
          itemIds: ["item-1"],
          method: "CASH",
        });
        return Promise.resolve({ id: "pay-1" });
      }
      if (path === "/api/v1/payments/pay-1/receipt")
        return Promise.resolve(receipt);
      if (!path.startsWith("/api/v1/patient-profiles"))
        return actual.apiRequest(path, init);
      if (path.startsWith("/api/v1/patient-profiles?"))
        return Promise.resolve({ content: [minhAnh] });
      if (path === "/api/v1/patient-profiles/BN0001")
        return Promise.resolve(minhAnh);
      return Promise.reject(new Error(`Unexpected request: ${path}`));
    },
  };
});
beforeEach(() => {
  navigation.query = "";
  resetReceptionMock(createReceptionSeed("2026-10-24"));
});
function mount(section: Parameters<typeof ReceptionScreen>[0]["section"]) {
  return render(
    <ReceptionProvider>
      <ReceptionScreen section={section} />
    </ReceptionProvider>,
  );
}

describe("receptionist screens with real mock adapter", () => {
  it("ở production không có cờ demo, màn còn dữ liệu giả bị khoá", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "");
    try {
      mount("notifications");
      expect(
        await screen.findByRole("heading", {
          name: "Khu vực lễ tân chưa được kích hoạt",
        }),
      ).toBeInTheDocument();
    } finally {
      vi.unstubAllEnvs();
    }
  });
  it("searches by phone alone and reveals administrative details only", async () => {
    mount("patients");
    await screen.findByRole("heading", { name: "Hồ sơ bệnh nhân" });
    fireEvent.change(
      screen.getByLabelText("Tên, số điện thoại hoặc mã bệnh nhân"),
      {
        target: { value: "0900000001" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    expect(await screen.findByText("Nguyễn Minh Anh")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Mở hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Chưa cung cấp")).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/dị ứng|tiền sử/i),
    ).not.toBeInTheDocument();
  });
  it("records cash only after explicit confirmation and shows an immutable receipt", async () => {
    const user = userEvent.setup();
    mount("payments");
    await screen.findByRole("heading", { name: "Thu phí tại quầy" });
    await user.type(
      screen.getByLabelText("Tên hoặc số điện thoại"),
      "0900000001",
    );
    await user.click(
      await screen.findByRole("button", { name: /Nguyễn Minh Anh/ }),
    );
    await user.click(await screen.findByRole("button", { name: "Thu tiền" }));
    const dialog = await screen.findByRole("dialog", {
      name: "Thu tiền tại quầy",
    });
    expect(
      within(dialog).getByRole("checkbox", { name: /Phí khám/ }),
    ).toBeChecked();
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    ).toBeDisabled();
    await user.click(
      within(dialog).getByRole("checkbox", { name: /Đã nhận tiền/ }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    );
    const receipt = await screen.findByRole("dialog", {
      name: "Biên lai thu tiền",
    });
    expect(await within(receipt).findByText("RC-1")).toBeInTheDocument();
    expect(within(receipt).getByText("Phí khám")).toBeInTheDocument();
    expect(within(receipt).queryByRole("textbox")).not.toBeInTheDocument();
  });
  it("marks notifications read only through the adapter", async () => {
    mount("notifications");
    await screen.findByRole("heading", { name: "Thông báo" });
    fireEvent.click(
      screen.getAllByRole("button", { name: "Đọc thông báo" })[0],
    );
    await screen.findByRole("dialog");
    expect((await loadReception()).notices[0].read).toBe(true);
  });
});
