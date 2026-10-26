import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { listAccounts } from "../api/accountApi";
import {
  NON_EXISTENT_ACCOUNT_ID,
  resetAccountMockStore,
} from "../api/accountMockData";
import { AccountDetailSheet } from "./AccountDetailSheet";
import { formatAppMessage } from "@/lib/messages/appMessages";
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const onClose = vi.fn();
const onChanged = vi.fn();
const onStale = vi.fn();

async function idOf(q: string) {
  return (await listAccounts({ q })).items[0].id;
}

function Harness({ id }: { id: string }) {
  const [open, setOpen] = useState(true);
  return (
    <>
      <Toaster />
      {open ? (
        <AccountDetailSheet
          accountId={id}
          onClose={() => {
            onClose();
            setOpen(false);
          }}
          onChanged={onChanged}
          onStale={onStale}
        />
      ) : null}
    </>
  );
}

const button = (name: string) => screen.queryByRole("button", { name });

beforeEach(() => {
  resetAccountMockStore();
  vi.clearAllMocks();
});

describe("AccountDetailSheet", () => {
  it("chỉ hiện nút có trong allowedActions (nhân viên vô hiệu hóa, chưa có hồ sơ)", async () => {
    render(<Harness id={await idOf("kttd.cuong")} />);
    expect(
      await screen.findByText("Chưa có hồ sơ nhân viên."),
    ).toBeInTheDocument();

    expect(button("Kích hoạt")).toBeInTheDocument();
    expect(button("Cấp lại mật khẩu")).toBeInTheDocument();
    expect(button("Tạo hồ sơ nhân viên")).toBeInTheDocument();
    expect(button("Vô hiệu hóa")).not.toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Đóng" }).length,
    ).toBeGreaterThan(0);
  });

  it("bệnh nhân: không có Cấp lại mật khẩu, Tạo hồ sơ; có email, không có tên đăng nhập", async () => {
    render(<Harness id={await idOf("lan.tran@")} />);
    expect(await screen.findByText("lan.tran@mail.vn")).toBeInTheDocument();

    expect(button("Vô hiệu hóa")).toBeInTheDocument();
    expect(button("Cấp lại mật khẩu")).not.toBeInTheDocument();
    expect(button("Tạo hồ sơ nhân viên")).not.toBeInTheDocument();
    expect(screen.queryByText("Tên đăng nhập")).not.toBeInTheDocument();
  });

  it("Tạo hồ sơ nhân viên chỉ điều hướng, URL chỉ có accountId", async () => {
    const user = userEvent.setup();
    const id = await idOf("kttd.cuong");
    render(<Harness id={id} />);
    await user.click(
      await screen.findByRole("button", { name: "Tạo hồ sơ nhân viên" }),
    );

    expect(push).toHaveBeenCalledWith(`/admin/staff-profiles?accountId=${id}`);
    expect(push.mock.calls[0][0]).not.toContain("kttd");
  });

  it("Cấp lại mật khẩu: xác nhận rồi hiện mật khẩu mới một lần", async () => {
    const user = userEvent.setup();
    render(<Harness id={await idOf("ktxn.dung")} />);
    await user.click(
      await screen.findByRole("button", { name: "Cấp lại mật khẩu" }),
    );

    // Hộp xác nhận: chưa cấp khi chưa bấm.
    expect(await screen.findByText("Cấp lại mật khẩu?")).toBeInTheDocument();
    const confirm = screen
      .getAllByRole("button", { name: "Cấp lại mật khẩu" })
      .at(-1)!;
    await user.click(confirm);

    expect(await screen.findByText("Mật khẩu mới")).toBeInTheDocument();
    expect(
      screen.getByText(/Đóng hộp thoại này là mất mật khẩu/),
    ).toBeInTheDocument();
    const password = document.querySelector("dd.font-mono")!.textContent!;
    expect(password).toHaveLength(12);

    await user.click(screen.getByRole("button", { name: "Xong" }));
    await waitFor(() =>
      expect(screen.queryByText(password)).not.toBeInTheDocument(),
    );
  });

  it("Vô hiệu hóa trong Sheet: xác nhận -> cập nhật Sheet và báo danh sách", async () => {
    const user = userEvent.setup();
    render(<Harness id={await idOf("letan.an")} />);
    await user.click(
      await screen.findByRole("button", { name: "Vô hiệu hóa" }),
    );
    const confirm = (
      await screen.findAllByRole("button", { name: "Vô hiệu hóa" })
    ).at(-1)!;
    await user.click(confirm);

    expect(
      await screen.findByText(
        "Trạng thái tài khoản đã được cập nhật thành Đã vô hiệu hóa.",
      ),
    ).toBeInTheDocument();
    expect(onChanged).toHaveBeenCalledWith(
      expect.objectContaining({ status: "INACTIVE" }),
    );
    expect(
      await screen.findByRole("button", { name: "Kích hoạt" }),
    ).toBeInTheDocument();
  });

  it("tài khoản không còn (404) -> toast, đóng Sheet và tải lại danh sách", async () => {
    render(<Harness id={NON_EXISTENT_ACCOUNT_ID} />);
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onStale).toHaveBeenCalled();
    expect(
      await screen.findByText(formatAppMessage("MSG-14")),
    ).toBeInTheDocument();
  });
});
