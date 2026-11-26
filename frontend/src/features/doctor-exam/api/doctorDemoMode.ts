// Runtime Bác sĩ luôn dùng API thật. Giữ các export này cho component cũ
// trong lúc loại bỏ nhánh demo khỏi mã nguồn.
export function isDoctorDemoMode(): boolean {
  return false;
}

export function canToggleDoctorDemo(): boolean {
  return false;
}

export function setDoctorDemoMode(on: boolean): void {
  void on;
}

export function subscribeDoctorDemoMode(onChange: () => void): () => void {
  void onChange;
  return () => {};
}
