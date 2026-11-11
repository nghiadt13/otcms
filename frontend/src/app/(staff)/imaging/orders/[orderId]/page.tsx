import { AssignedUploadWorkDetailScreen } from "@/features/imaging/components/AssignedUploadWorkDetailScreen";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <AssignedUploadWorkDetailScreen requestId={orderId} />;
}
