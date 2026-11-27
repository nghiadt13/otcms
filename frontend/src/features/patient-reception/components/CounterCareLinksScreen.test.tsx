import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { CounterCareLinksScreen } from "./CounterCareLinksScreen";
import { ReceptionProvider } from "./ReceptionProvider";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

it("chỉ xác nhận đúng yêu cầu sau khi lễ tân đánh dấu đã đối chiếu", async () => {
  vi.mocked(apiRequest)
    .mockResolvedValueOnce({
      items: [
        {
          linkId: "link-123",
          fullName: "Nguyễn Minh Khoa QA",
          dateOfBirth: "2015-11-23",
          nationalId: "079215000123",
          identityDocumentType: "CCCD",
          relationship: "CHILD",
          requestedAt: "2026-11-27T15:00:36Z",
          requesterName: "Người thân QA",
          requesterEmail: "parent@example.test",
        },
      ],
    })
    .mockResolvedValueOnce({ result: "LINKED" });

  render(
    <ReceptionProvider>
      <CounterCareLinksScreen />
    </ReceptionProvider>,
  );
  fireEvent.change(
    screen.getByLabelText("Tên hoặc số giấy tờ của người được thêm"),
    {
      target: { value: "079215000123" },
    },
  );
  fireEvent.click(screen.getByRole("button", { name: "Tìm yêu cầu" }));
  expect(apiRequest).toHaveBeenCalledWith(
    "/api/v1/care-links/pending?query=079215000123",
  );
  fireEvent.click(await screen.findByRole("button", { name: "Xác minh" }));

  const dialog = within(screen.getByRole("dialog"));
  expect(dialog.getByText("parent@example.test")).toBeInTheDocument();
  const confirm = dialog.getByRole("button", { name: "Xác nhận liên kết" });
  expect(confirm).toBeDisabled();
  fireEvent.click(dialog.getByRole("checkbox"));
  expect(confirm).toBeEnabled();
  fireEvent.click(confirm);

  expect(
    await screen.findByText("Không có yêu cầu đang chờ xác minh phù hợp."),
  ).toBeInTheDocument();
  expect(apiRequest).toHaveBeenLastCalledWith(
    "/api/v1/care-links/link-123/counter-confirmation",
    {
      method: "POST",
      body: JSON.stringify({ identityChecked: true }),
    },
  );
});
