import { UploadScreen } from "@/features/imaging/components/UploadScreens";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <UploadScreen id={orderId} />;
}
