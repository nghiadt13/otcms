import { TechnicianImagingUploadScreen } from "@/features/imaging/components/TechnicianImagingUploadScreen";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <TechnicianImagingUploadScreen requestId={orderId} />;
}
