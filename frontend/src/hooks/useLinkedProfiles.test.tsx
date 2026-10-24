import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  LINKED_PROFILE_VARIANTS,
  replaceLinkedProfilesMock,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { useLinkedProfiles } from "./useLinkedProfiles";
import { formatAppMessage } from "@/lib/messages/appMessages";
function Harness() {
  const { profiles, loading, error, reload } = useLinkedProfiles();
  return (
    <div>
      {loading ? <p>đang tải</p> : null}
      <ul>
        {profiles.map((profile) => (
          <li key={profile.id}>{profile.fullName}</li>
        ))}
      </ul>
      {error ? <p role="alert">{error}</p> : null}
      <button onClick={reload} type="button">
        Tải lại
      </button>
    </div>
  );
}

beforeEach(() => {
  resetLinkedProfilesMock();
});

describe("useLinkedProfiles", () => {
  it("tải hồ sơ khi mount, bản thân đứng đầu", async () => {
    render(<Harness />);
    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    const names = screen
      .getAllByRole("listitem")
      .map((item) => item.textContent);
    expect(names).toEqual([
      "Nguyễn Văn An",
      "Nguyễn Gia Huy",
      "Nguyễn Thị Lan",
    ]);
    expect(screen.queryByText("đang tải")).not.toBeInTheDocument();
  });

  it("đổi kho hồ sơ thì hook tự tải lại mà không cần reload", async () => {
    render(<Harness />);
    await screen.findByText("Nguyễn Thị Lan");

    act(() => replaceLinkedProfilesMock(LINKED_PROFILE_VARIANTS.selfOnly));

    await waitFor(() =>
      expect(screen.queryByText("Nguyễn Thị Lan")).not.toBeInTheDocument(),
    );
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
  });

  it("lỗi tải hiện câu MSG-04 và nút tải lại lấy được dữ liệu", async () => {
    resetLinkedProfilesMock({ failList: true });
    render(<Harness />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );

    resetLinkedProfilesMock();
    await userEvent.click(screen.getByRole("button", { name: "Tải lại" }));
    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
