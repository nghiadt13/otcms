import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { ImagingViewerPage } from "./ImagingViewerPage";

it("provides return navigation and a fullscreen control around OHIF", () => {
  render(
    <ImagingViewerPage
      studyInstanceUid="1.2.840.123"
      returnTo="/doctor/visits/visit-id"
    />,
  );

  expect(
    screen.getByRole("button", { name: "Quay lại OTCMS" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Xem toàn màn hình" }),
  ).toBeInTheDocument();
  expect(screen.getByTitle("OHIF DICOM Viewer")).toHaveAttribute(
    "allow",
    "fullscreen",
  );
});
