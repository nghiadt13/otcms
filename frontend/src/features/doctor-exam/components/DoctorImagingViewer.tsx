"use client";
import { createContext, useContext, type ReactNode } from "react";
type RenderViewer = (studyRef: string) => ReactNode;
const ViewerContext = createContext<RenderViewer | null>(null);
export function DoctorImagingViewerProvider({
  renderViewer,
  children,
}: {
  renderViewer: RenderViewer;
  children: ReactNode;
}) {
  return (
    <ViewerContext.Provider value={renderViewer}>
      {children}
    </ViewerContext.Provider>
  );
}
export function DoctorImagingViewer({ studyRef }: { studyRef: string }) {
  const renderViewer = useContext(ViewerContext);
  return renderViewer ? (
    renderViewer(studyRef)
  ) : (
    <p role="alert">Trình xem ảnh chưa sẵn sàng.</p>
  );
}
