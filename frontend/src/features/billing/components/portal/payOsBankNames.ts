// Tên ngân hàng theo mã BIN (NAPAS) mà PayOS trả. Chỉ gồm các ngân hàng phổ biến; BIN lạ hiện
// "Ngân hàng (BIN …)" để người dùng vẫn đối chiếu được.
const BANKS: Record<string, string> = {
  "970403": "Sacombank",
  "970405": "Agribank",
  "970407": "Techcombank",
  "970414": "Oceanbank",
  "970415": "VietinBank",
  "970416": "ACB",
  "970418": "BIDV",
  "970419": "NCB",
  "970422": "MB Bank",
  "970423": "TPBank",
  "970424": "Shinhan Bank",
  "970425": "ABBANK",
  "970426": "MSB",
  "970427": "VietABank",
  "970428": "NamABank",
  "970429": "SCB",
  "970431": "Eximbank",
  "970432": "VPBank",
  "970433": "VietBank",
  "970436": "Vietcombank",
  "970437": "HDBank",
  "970438": "BaoVietBank",
  "970439": "PublicBank",
  "970440": "SeABank",
  "970441": "VIB",
  "970443": "SHB",
  "970448": "OCB",
  "970449": "LPBank",
  "970452": "KienlongBank",
  "970454": "VietCapitalBank",
  "546034": "CAKE by VPBank",
  "963388": "Timo",
};

export function bankNameFromBin(bin: string | null | undefined): string {
  if (!bin) return "Ngân hàng";
  return BANKS[bin] ?? `Ngân hàng (BIN ${bin})`;
}
