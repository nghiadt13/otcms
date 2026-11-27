import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LaboratoryResultDraft } from "./LaboratoryResultDraft";

const apiRequest = vi.hoisted(() => vi.fn());
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("sonner", () => ({ toast }));

beforeEach(() => {
  apiRequest.mockReset();
  apiRequest.mockResolvedValue({
    values: [
      {
        indicator: "CRP (Protein phản ứng C)",
        value: "",
        unit: "mg/L",
        referenceRange: "0 - 5",
        comment: "",
        abnormal: null,
      },
    ],
  });
});

describe("LaboratoryResultDraft result validation", () => {
  it("does not show a validation warning for an untouched empty result", async () => {
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");

    expect(result).toHaveValue("");
    expect(result).toHaveAttribute("inputmode", "decimal");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưu bản nháp" })).toBeDisabled();
  });

  it("keeps a legacy invalid draft visible and waits for interaction before warning", async () => {
    apiRequest.mockResolvedValueOnce({
      values: [
        {
          indicator: "CRP (Protein phản ứng C)",
          value: "legacy text",
          unit: "mg/L",
          referenceRange: "0 - 5",
          comment: "",
          abnormal: null,
        },
      ],
    });
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");

    expect(result).toHaveValue("legacy text");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    fireEvent.blur(result);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Kết quả phải là số không âm",
    );
  });

  it("blocks saving a negative result and explains the constraint", async () => {
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");

    expect(result).toHaveAttribute("type", "text");
    fireEvent.change(result, { target: { value: "-3.5" } });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Kết quả phải là số không âm",
    );
    expect(screen.getByRole("button", { name: "Lưu bản nháp" })).toBeDisabled();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("allows a non-negative decimal result to be saved", async () => {
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");
    fireEvent.change(result, { target: { value: "3.5" } });

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Lưu bản nháp" }),
      ).toBeEnabled(),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("does not save alphabetic input", async () => {
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");
    fireEvent.change(result, { target: { value: "abc" } });

    expect(screen.getByRole("button", { name: "Lưu bản nháp" })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kết quả phải là số không âm",
    );
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("shows a required message only after an empty result is touched", async () => {
    render(<LaboratoryResultDraft orderId="order-1" />);
    const result = await screen.findByLabelText("Kết quả");

    fireEvent.focus(result);
    fireEvent.blur(result);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Vui lòng nhập kết quả.",
    );
  });
});
