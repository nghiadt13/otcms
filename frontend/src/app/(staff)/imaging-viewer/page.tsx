import { ImagingViewerPage } from "@/features/imaging/components/ImagingViewerPage";

interface PageProps {
  searchParams: Promise<{
    studyInstanceUid?: string;
    returnTo?: string;
  }>;
}

export default async function Page({ searchParams }: PageProps) {
  const params = await searchParams;
  return (
    <ImagingViewerPage
      studyInstanceUid={params.studyInstanceUid ?? ""}
      returnTo={params.returnTo ?? ""}
    />
  );
}
