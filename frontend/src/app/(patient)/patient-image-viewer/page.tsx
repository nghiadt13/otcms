import { ImagingViewerPage } from "@/features/imaging/components/ImagingViewerPage";
import { PatientAuthGate } from "@/features/patient-shell/components/PatientAuthGate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

interface PageProps {
  searchParams: Promise<{
    studyInstanceUid?: string;
    returnTo?: string;
  }>;
}

export default async function PatientImageViewerRoute({
  searchParams,
}: PageProps) {
  const params = await searchParams;
  return (
    <PatientAuthGate>
      <ImagingViewerPage
        studyInstanceUid={params.studyInstanceUid ?? ""}
        returnTo={params.returnTo ?? ""}
        fallbackPath={
          params.returnTo?.startsWith("/otcms/")
            ? `/otcms${PATIENT_ROUTES.results}`
            : PATIENT_ROUTES.results
        }
      />
    </PatientAuthGate>
  );
}
