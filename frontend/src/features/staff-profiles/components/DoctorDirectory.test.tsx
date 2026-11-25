import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { PublicDoctor } from "../types/publicDoctor";
import { DoctorDirectory } from "./DoctorDirectory";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));

const doctor = (id: string, fullName: string): PublicDoctor => ({
  id,
  fullName,
  photoUrl: null,
  specialties: ["Khớp gối"],
  qualifications: "BSCKI",
  biography: "",
  consultationInfo: "",
});
const DOCTORS = [
  doctor("doc-1", "Trần Văn Minh"),
  doctor("doc-2", "Lê Thị Hà"),
];

describe("DoctorDirectory", () => {
  it("hiện hồ sơ bác sĩ và không tải giờ trống cho danh bạ", () => {
    render(<DoctorDirectory doctors={DOCTORS} />);
    expect(screen.getByText("Trần Văn Minh")).toBeInTheDocument();
    expect(screen.getByText("Lê Thị Hà")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Chi tiết về bác sĩ Trần Văn Minh" }))
      .toHaveAttribute("href", "/doctors/doc-1");
    expect(screen.getByRole("link", { name: "Chi tiết về bác sĩ Lê Thị Hà" }))
      .toHaveAttribute("href", "/doctors/doc-2");
    expect(screen.queryByText(/lịch 7 ngày|Hết giờ trong 7 ngày/)).not.toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });

  it("hiện hướng dẫn khi chưa có bác sĩ công khai", () => {
    render(<DoctorDirectory doctors={[]} />);
    expect(screen.getByText("Chưa có bác sĩ nào được công khai hồ sơ.")).toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });
});
