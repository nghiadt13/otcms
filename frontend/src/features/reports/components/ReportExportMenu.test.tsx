import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { ReportExportMenu } from "./ReportExportMenu";

it("offers only the approved PDF format and forwards the selection", async () => {
  const user = userEvent.setup();
  const exportFile = vi.fn();
  render(
    <ReportExportMenu
      exporting={false}
      formats={["pdf"]}
      onExport={exportFile}
    />,
  );
  await user.click(screen.getByRole("button", { name: "Xuất báo cáo" }));
  expect(screen.queryByRole("button", { name: /Excel/ })).toBeNull();
  await user.click(screen.getByRole("button", { name: "PDF" }));
  expect(exportFile).toHaveBeenCalledWith("pdf");
});
