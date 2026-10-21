import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as api from "../api/websiteContentAdminApi";
import {
  NOT_FOUND_CONTENT_ID,
  resetWebsiteContentStore,
  websiteContentStore,
} from "../api/websiteContentAdminMockData";
import { WebsiteContentManagement } from "./WebsiteContentManagement";

const SLOW = { timeout: 4000 };
const PUBLISHED = "Nội dung trên website đã được cập nhật.";

const renderScreen = () =>
  render(
    <>
      <Toaster />
      <WebsiteContentManagement />
    </>,
  );
const loaded = () => screen.findByText(/Trang 1\/2/, undefined, SLOW);
const sheet = () => screen.findByRole("dialog", { name: /nội dung$/i }, SLOW);

beforeEach(() => {
  resetWebsiteContentStore();
  vi.restoreAllMocks();
});

describe("WebsiteContentManagement - danh sách", () => {
  it("hiện cột đúng spec, một nút chính, không có nút bị cấm", async () => {
    renderScreen();
    await loaded();
    for (const name of [
      "Tiêu đề",
      "Loại",
      "Ngày đăng",
      "Cập nhật lần cuối",
      "Người đăng",
    ])
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "Thêm nội dung" }),
    ).toHaveLength(1);
    for (const banned of [/xóa/i, /^ẩn/i, /nháp/i, /lên lịch/i])
      expect(screen.queryByRole("button", { name: banned })).toBeNull();
  });

  it("trống chưa có nội dung và trống do lọc dạy cách dùng", async () => {
    resetWebsiteContentStore([]);
    renderScreen();
    expect(
      await screen.findByText(
        "Chưa có nội dung nào. Bấm Thêm nội dung để đăng nội dung đầu tiên.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
  });

  it("lọc không khớp hiện câu trống do lọc", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await user.click(
      screen.getByRole("searchbox", { name: "Tìm nội dung theo tiêu đề" }),
    );
    await user.paste("khong-co-bai-nao");
    expect(
      await screen.findByText(
        "Không có nội dung khớp bộ lọc. Đổi từ khóa hoặc chọn loại khác.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
  });

  it("lỗi tải hiện MSG-04 và Thử lại gọi lại API", async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(api, "listWebsiteContents");
    renderScreen();
    await loaded();
    await user.click(
      screen.getByRole("searchbox", { name: "Tìm nội dung theo tiêu đề" }),
    );
    await user.paste("loi-dich-vu");
    expect(
      await screen.findByText(/Hệ thống đang gặp sự cố/, undefined, SLOW),
    ).toBeInTheDocument();
    const calls = spy.mock.calls.length;
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(
      () => expect(spy.mock.calls.length).toBeGreaterThan(calls),
      SLOW,
    );
  });

  it("'Xem trên website' ẩn khi thiếu viewOnWebsite và mở tab mới có noopener", async () => {
    const user = userEvent.setup();
    const open = vi.spyOn(window, "open").mockReturnValue(null);
    renderScreen();
    await loaded();
    const editOnly = websiteContentStore().find(
      (i) => !i.allowedActions.includes("viewOnWebsite"),
    )!;
    await user.click(
      screen.getByRole("button", { name: `Thao tác cho ${editOnly.title}` }),
    );
    expect(
      await screen.findByRole("menuitem", { name: "Sửa" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Xem trên website" }),
    ).toBeNull();
    await user.keyboard("{Escape}");

    const both = websiteContentStore().find((i) => i.type === "ABOUT_PAGE")!;
    await user.click(
      screen.getByRole("button", { name: `Thao tác cho ${both.title}` }),
    );
    await user.click(
      await screen.findByRole("menuitem", { name: "Xem trên website" }),
    );
    expect(open).toHaveBeenCalledWith(
      "/gioi-thieu",
      "_blank",
      "noopener,noreferrer",
    );
  });
});

describe("WebsiteContentManagement - soạn và đăng", () => {
  it("thiếu tiêu đề: MSG-69 dưới ô, không mở hộp xác nhận", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await user.click(screen.getByRole("button", { name: "Thêm nội dung" }));
    const dialog = await sheet();
    expect(
      within(dialog).getByText("Chọn loại nội dung để bắt đầu soạn."),
    ).toBeInTheDocument();
    await user.selectOptions(
      within(dialog).getByLabelText(/Loại nội dung/),
      "ABOUT_PAGE",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Đăng nội dung" }),
    );
    expect(
      within(dialog).getAllByText("Nội dung thiếu hoặc chưa phù hợp để đăng.")
        .length,
    ).toBeGreaterThan(0);
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("thêm bài: xác nhận rồi POST, toast MSG-68, đóng Sheet, danh sách tải lại; Xem trước không gọi API", async () => {
    const user = userEvent.setup();
    const create = vi.spyOn(api, "createWebsiteContent");
    renderScreen();
    await loaded();
    await user.click(screen.getByRole("button", { name: "Thêm nội dung" }));
    const dialog = await sheet();
    await user.selectOptions(
      within(dialog).getByLabelText(/Loại nội dung/),
      "HEALTH_ARTICLE",
    );
    await user.type(within(dialog).getByLabelText(/Tiêu đề/), "Bài kiểm thử");
    await user.click(within(dialog).getByRole("textbox", { name: "Nội dung" }));
    await user.paste("Đoạn **quan trọng**");

    await user.click(within(dialog).getByRole("button", { name: "Xem trước" }));
    const preview = await screen.findByRole("dialog", { name: /Xem trước/ });
    expect(within(preview).getByText("quan trọng")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
    await user.click(within(preview).getByRole("button", { name: "Đóng" }));

    await user.click(
      within(dialog).getByRole("button", { name: "Đăng nội dung" }),
    );
    const confirm = await screen.findByRole("alertdialog");
    expect(
      within(confirm).getByText("Nội dung sẽ hiển thị công khai ngay."),
    ).toBeInTheDocument();
    await user.click(
      within(confirm).getByRole("button", { name: "Đăng nội dung" }),
    );

    expect(
      await screen.findByText(PUBLISHED, undefined, SLOW),
    ).toBeInTheDocument();
    expect(create).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull(), SLOW);
    expect(
      await screen.findByText("Bài kiểm thử", undefined, SLOW),
    ).toBeInTheDocument();
  });

  it("sửa: Loại chỉ đọc, dữ liệu đúng; PUT không gửi type", async () => {
    const user = userEvent.setup();
    const update = vi.spyOn(api, "updateWebsiteContent");
    renderScreen();
    await loaded();
    const target = websiteContentStore().find((i) => i.type === "ABOUT_PAGE")!;
    await user.click(
      screen.getByRole("button", { name: `Thao tác cho ${target.title}` }),
    );
    await user.click(await screen.findByRole("menuitem", { name: "Sửa" }));
    const dialog = await sheet();
    const type = await within(dialog).findByLabelText(
      /Loại nội dung/,
      undefined,
      SLOW,
    );
    expect(type).toBeDisabled();
    expect(type).toHaveValue("Trang giới thiệu");
    expect(within(dialog).getByLabelText(/Tiêu đề/)).toHaveValue(target.title);
    expect(
      within(dialog).getByRole("button", { name: "Đăng bản cập nhật" }),
    ).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/Tiêu đề/), " (sửa)");
    await user.click(
      within(dialog).getByRole("button", { name: "Đăng bản cập nhật" }),
    );
    const confirm = await screen.findByRole("alertdialog");
    await user.click(
      within(confirm).getByRole("button", { name: "Đăng bản cập nhật" }),
    );
    expect(
      await screen.findByText(PUBLISHED, undefined, SLOW),
    ).toBeInTheDocument();
    expect(update).toHaveBeenCalledWith(
      target.id,
      expect.not.objectContaining({ type: expect.anything() }),
    );
  });

  it("sửa nội dung không còn (404): toast MSG-50 và đóng Sheet", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    const target = websiteContentStore()[3];
    // Giả lập bản ghi bị xóa phía BE sau khi danh sách đã tải.
    target.id = NOT_FOUND_CONTENT_ID;
    await user.click(
      screen.getByRole("button", { name: `Thao tác cho ${target.title}` }),
    );
    await user.click(await screen.findByRole("menuitem", { name: "Sửa" }));
    expect(
      await screen.findByText(
        "Nội dung không còn hoặc bạn không đủ quyền truy cập.",
        undefined,
        SLOW,
      ),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("Hủy khi đã nhập hỏi 'Bỏ thay đổi?'; 'Tiếp tục soạn' giữ form", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await user.click(screen.getByRole("button", { name: "Thêm nội dung" }));
    const dialog = await sheet();
    await user.type(within(dialog).getByLabelText(/Tiêu đề/), "Dở dang");
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    const confirm = await screen.findByRole("alertdialog");
    expect(within(confirm).getByText("Bỏ thay đổi?")).toBeInTheDocument();
    await user.click(
      within(confirm).getByRole("button", { name: "Tiếp tục soạn" }),
    );
    expect(within(dialog).getByLabelText(/Tiêu đề/)).toHaveValue("Dở dang");

    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await user.click(
      await screen.findByRole("button", { name: "Bỏ thay đổi" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("tải ảnh sai định dạng hiện MSG-69 dưới ô ảnh", async () => {
    const user = userEvent.setup();
    renderScreen();
    await loaded();
    await user.click(screen.getByRole("button", { name: "Thêm nội dung" }));
    const dialog = await sheet();
    await user.selectOptions(
      within(dialog).getByLabelText(/Loại nội dung/),
      "HEALTH_ARTICLE",
    );
    const input = within(dialog).getByLabelText("Ảnh minh họa");
    // `applyAccept: false`: bỏ lọc `accept` của trình duyệt để kiểm tra lớp kiểm của ứng dụng.
    await userEvent
      .setup({ applyAccept: false })
      .upload(input, new File(["x"], "a.svg", { type: "image/svg+xml" }));
    expect(
      await within(dialog).findByText(
        /Nội dung thiếu hoặc chưa phù hợp để đăng\. Chọn ảnh JPG/,
      ),
    ).toBeInTheDocument();
  });
});
