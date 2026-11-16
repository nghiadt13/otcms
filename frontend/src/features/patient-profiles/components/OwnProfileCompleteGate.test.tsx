import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import {
  findClinicRecord,
  resetPatientProfileMock,
  saveClinicRecord,
} from "../api/patientProfileMockStore";
import { OwnProfileCompleteGate } from "./OwnProfileCompleteGate";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/portal/appointments/new",
}));

function renderGate() {
  return render(
    <OwnProfileCompleteGate>
      <p>Màn đặt lịch</p>
    </OwnProfileCompleteGate>,
  );
}

beforeEach(() => {
  replace.mockClear();
  resetLinkedProfilesMock();
  resetPatientProfileMock();
});

describe("OwnProfileCompleteGate", () => {
  it("hồ sơ Bản thân thiếu CCCD: chuyển tới Thông tin cá nhân kèm next, không hiện màn đặt lịch", async () => {
    renderGate();

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith(
        "/portal/account?next=%2Fportal%2Fappointments%2Fnew",
      ),
    );
    expect(screen.queryByText("Màn đặt lịch")).toBeNull();
  });

  it("hồ sơ Bản thân đủ CCCD: cho vào màn đặt lịch", async () => {
    const an = findClinicRecord(PROFILE_MOCK_IDS.an);
    if (!an) throw new Error("Thiếu hồ sơ mẫu An.");
    saveClinicRecord({ ...an, citizenId: "079090001234" });
    renderGate();

    expect(await screen.findByText("Màn đặt lịch")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
