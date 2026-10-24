import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import {
  LINKED_PROFILE_VARIANTS,
  replaceLinkedProfilesMock,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import { ProfileScopeSelect } from "./ProfileScopeSelect";

const [SELF, PARENT, CHILD] = LINKED_PROFILE_VARIANTS.default;

const SPOUSE: LinkedProfile = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f09",
  patientCode: "BN-000999",
  fullName: "Trần Thị Mai",
  dateOfBirth: "1994-11-02",
  sex: "FEMALE",
  relationship: "SPOUSE",
};

beforeEach(() => {
  resetLinkedProfilesMock();
});

describe("ProfileScopeSelect", () => {
  it("có Tất cả và mỗi hồ sơ kèm quan hệ", () => {
    render(
      <ProfileScopeSelect
        onChange={() => {}}
        profiles={[SELF, PARENT, CHILD]}
        value={undefined}
      />,
    );
    const select = screen.getByLabelText("Người khám");
    expect(select).toHaveValue("");
    expect(screen.getByRole("option", { name: "Tất cả" })).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Nguyễn Thị Lan · Cha/Mẹ" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Nguyễn Văn An · Bản thân" }),
    ).toBeInTheDocument();
  });

  it("chọn một hồ sơ gọi onChange với mã hồ sơ, chọn Tất cả gọi với undefined", async () => {
    const onChange = vi.fn();
    render(
      <ProfileScopeSelect
        onChange={onChange}
        profiles={[SELF, PARENT]}
        value={SELF.id}
      />,
    );
    const select = screen.getByLabelText("Người khám");

    await userEvent.selectOptions(select, PARENT.id);
    expect(onChange).toHaveBeenLastCalledWith(PARENT.id);

    await userEvent.selectOptions(select, "");
    expect(onChange).toHaveBeenLastCalledWith(undefined);
  });

  it("ẩn khi chỉ có một hồ sơ hoặc không có hồ sơ nào", () => {
    const { rerender } = render(
      <ProfileScopeSelect
        onChange={() => {}}
        profiles={[SELF]}
        value={undefined}
      />,
    );
    expect(screen.queryByLabelText("Người khám")).not.toBeInTheDocument();

    rerender(
      <ProfileScopeSelect
        onChange={() => {}}
        profiles={[]}
        value={undefined}
      />,
    );
    expect(screen.queryByLabelText("Người khám")).not.toBeInTheDocument();
  });
});

function ScopeWithStore() {
  const { profiles, loading } = useLinkedProfiles();
  if (loading) return <p>đang tải</p>;
  return (
    <ProfileScopeSelect
      onChange={() => {}}
      profiles={profiles}
      value={undefined}
    />
  );
}

describe("ProfileScopeSelect với kho hồ sơ giả", () => {
  it("đổi kho thì danh sách lựa chọn cập nhật ngay", async () => {
    render(<ScopeWithStore />);
    expect(
      await screen.findByRole("option", { name: "Nguyễn Thị Lan · Cha/Mẹ" }),
    ).toBeInTheDocument();

    act(() =>
      replaceLinkedProfilesMock([...LINKED_PROFILE_VARIANTS.default, SPOUSE]),
    );

    expect(
      await screen.findByRole("option", { name: "Trần Thị Mai · Vợ/Chồng" }),
    ).toBeInTheDocument();

    act(() => replaceLinkedProfilesMock(LINKED_PROFILE_VARIANTS.selfOnly));
    await waitFor(() =>
      expect(screen.queryByLabelText("Người khám")).not.toBeInTheDocument(),
    );
  });
});
