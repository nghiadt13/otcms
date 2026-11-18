import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { IcdDiagnosisPicker } from "./IcdDiagnosisPicker";

const api = vi.hoisted(() => ({ apiRequest: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: api.apiRequest }));

// Danh mục BE trả về {code, name, group}; FE không có bản sao riêng.
const CATALOG = [
  { code: "M17.1", name: "Thoái hóa khớp gối nguyên phát", group: "M" },
  { code: "S83.5", name: "Tổn thương dây chằng chéo khớp gối", group: "S" },
];

beforeEach(() => {
  api.apiRequest.mockReset();
});

describe("chọn chẩn đoán ICD-10 từ danh mục BE (BR-39)", () => {
  it("tải danh mục từ API và hiện tên của mã đã chọn", async () => {
    api.apiRequest.mockResolvedValue(CATALOG);
    render(
      <IcdDiagnosisPicker
        value={[{ code: "M17.1", type: "PRIMARY" }]}
        onChange={vi.fn()}
      />,
    );
    expect(
      await screen.findByText("M17.1 · Thoái hóa khớp gối nguyên phát"),
    ).toBeInTheDocument();
    expect(api.apiRequest).toHaveBeenCalledWith(
      "/api/v1/clinical/icd10-codes",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("lỗi tải danh mục báo MSG-04 và khóa nút chọn chẩn đoán", async () => {
    api.apiRequest.mockRejectedValue(new ApiError(500, { code: "MSG-04" }));
    render(<IcdDiagnosisPicker value={[]} onChange={vi.fn()} />);
    expect(
      await screen.findByText(formatAppMessage("MSG-04")),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Chọn chẩn đoán chính" }),
    ).toBeDisabled();
  });
});
