import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { PatientAuthGate } from "./PatientAuthGate";

const { replace, fetchCurrentAccount } = vi.hoisted(() => ({
  replace: vi.fn(),
  fetchCurrentAccount: vi.fn(),
}));

// Ranh giới ngoài: router và lời gọi `GET /api/v1/me`; hook và cổng chạy thật.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/portal/appointments/new",
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  fetchCurrentAccount,
  peekCurrentAccount: () => null,
}));

const accountOf = (accountType: string) => ({
  id: "11111111-1111-1111-1111-111111111111",
  accountType,
  email: "an@example.com",
  displayName: "Nguyễn Văn An",
});

function renderGate() {
  return render(
    <PatientAuthGate>
      <p>Nội dung khu bệnh nhân</p>
    </PatientAuthGate>,
  );
}

beforeEach(() => {
  replace.mockClear();
  fetchCurrentAccount.mockReset();
});

describe("PatientAuthGate", () => {
  it("bệnh nhân đã đăng nhập thì vẽ nội dung", async () => {
    fetchCurrentAccount.mockResolvedValue(accountOf("PATIENT"));
    renderGate();
    expect(
      await screen.findByText("Nội dung khu bệnh nhân"),
    ).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("trong lúc kiểm tra không lộ nội dung", () => {
    fetchCurrentAccount.mockReturnValue(new Promise(() => undefined));
    renderGate();
    expect(screen.queryByText("Nội dung khu bệnh nhân")).toBeNull();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("chưa đăng nhập thì về /login kèm trang đang mở", async () => {
    fetchCurrentAccount.mockResolvedValue(null);
    renderGate();
    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith(
        "/login?next=%2Fportal%2Fappointments%2Fnew",
      ),
    );
    expect(screen.queryByText("Nội dung khu bệnh nhân")).toBeNull();
  });

  it("trang cho phép khách (anonymousPaths): vẽ nội dung, không đá về /login", async () => {
    fetchCurrentAccount.mockResolvedValue(null);
    render(
      <PatientAuthGate anonymousPaths={["/portal/appointments/new"]}>
        <p>Nội dung khu bệnh nhân</p>
      </PatientAuthGate>,
    );
    expect(
      await screen.findByText("Nội dung khu bệnh nhân"),
    ).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("backend trả 401 thì cũng về /login", async () => {
    fetchCurrentAccount.mockRejectedValue(new ApiError(401));
    renderGate();
    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith(
        expect.stringMatching(/^\/login\?next=/),
      ),
    );
  });

  it("tài khoản nhân viên được chuyển tới lối vào theo vai trò", async () => {
    fetchCurrentAccount.mockResolvedValue(accountOf("RECEPTIONIST"));
    renderGate();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/workspace"));
    expect(screen.queryByText("Nội dung khu bệnh nhân")).toBeNull();
  });

  it("lỗi đọc tài khoản: báo lỗi, không đá về đăng nhập, thử lại được", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    fetchCurrentAccount
      .mockRejectedValueOnce(new ApiError(500))
      .mockResolvedValueOnce(accountOf("PATIENT"));
    renderGate();
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByText("Nội dung khu bệnh nhân"),
    ).toBeInTheDocument();
  });
});
