import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetNotificationMockStore } from "../api/notificationApi";
import { MOCK_PATIENT_NOTIFICATIONS } from "../api/patientNotificationMockData";
import { PatientNotificationBell } from "./PatientNotificationBell";

vi.mock("next/navigation", () => ({ usePathname: () => "/portal" }));

beforeEach(() => {
  resetLinkedProfilesMock();
  resetNotificationMockStore(MOCK_PATIENT_NOTIFICATIONS);
});

describe("PatientNotificationBell", () => {
  it("chuông là liên kết tới trang thông báo, nhãn và số đếm nêu số chưa đọc", async () => {
    render(<PatientNotificationBell />);
    const bell = await screen.findByRole("link", {
      name: "Thông báo, 3 chưa đọc",
    });
    expect(bell).toHaveAttribute("href", "/portal/notifications");
    expect(bell).toHaveTextContent("3");
  });

  it("không có thông báo chưa đọc: nhãn chỉ là Thông báo, không có số đếm", async () => {
    resetNotificationMockStore(
      MOCK_PATIENT_NOTIFICATIONS.map((entry) => ({
        ...entry,
        read: true,
        allowedActions: [],
      })),
    );
    render(<PatientNotificationBell />);
    const bell = await screen.findByRole("link", { name: "Thông báo" });
    expect(bell).not.toHaveTextContent(/\d/);
  });

  it("tài khoản chưa có hồ sơ đã gắn không có chuông (BR-68)", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<PatientNotificationBell />);
    await waitFor(() =>
      expect(screen.queryByRole("link")).not.toBeInTheDocument(),
    );
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
