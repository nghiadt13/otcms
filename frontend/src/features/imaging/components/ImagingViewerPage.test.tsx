import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ImagingViewerPage } from "./ImagingViewerPage";

vi.mock("../api/imagingViewerApi", () => ({ imagingViewerPath: () => "about:blank" }));

afterEach(() => vi.unstubAllGlobals());

describe("ImagingViewerPage", () => {
  it("cho phép bật và thoát chế độ xem tối đa khi trình duyệt không hỗ trợ fullscreen", async () => {
    render(<ImagingViewerPage studyInstanceUid="1.2.3" returnTo="/portal/results" />);
    fireEvent.click(screen.getByRole("button", { name: "Toàn màn hình" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Thoát toàn màn hình" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Thoát toàn màn hình" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Toàn màn hình" })).toBeInTheDocument());
  });

  it.each([true, false])(
    "thu gọn bảng ảnh khi điện thoại ở chế độ dọc: %s",
    (portrait) => {
      vi.stubGlobal("matchMedia", () => ({ matches: portrait }));
      render(<ImagingViewerPage studyInstanceUid="1.2.3" returnTo="/portal/results" />);
      const frame = screen.getByTitle("OHIF DICOM Viewer") as HTMLIFrameElement;
      const doc = document.implementation.createHTMLDocument();
      Object.defineProperty(frame, "contentDocument", { value: doc });
      const panel = doc.createElement("div");
      panel.dataset.cy = "side-panel-header-left";
      panel.innerHTML = "<svg><title>icon-panel-close-left</title></svg>";
      const click = vi.fn();
      panel.addEventListener("click", click);
      doc.body.append(panel);

      fireEvent.load(frame);

      expect(click).toHaveBeenCalledTimes(portrait ? 1 : 0);
    },
  );
});
