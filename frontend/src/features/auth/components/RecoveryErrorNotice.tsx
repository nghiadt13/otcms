import type { RecoveryFailure } from "../api/passwordRecoveryApi";

const messages: Record<RecoveryFailure, string> = {
  "invalid-token":
    "Link đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu link mới.",
  "password-reuse": "Mật khẩu mới phải khác mật khẩu hiện tại.",
  validation: "Thông tin chưa hợp lệ. Vui lòng kiểm tra và thử lại.",
  "rate-limit": "Bạn đã gửi quá nhiều yêu cầu. Vui lòng chờ rồi thử lại.",
  "staff-network":
    "Khôi phục tài khoản nhân viên chỉ khả dụng trong mạng phòng khám hoặc VPN được cấp.",
  service: "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau.",
};

export function RecoveryErrorNotice({ reason }: { reason: RecoveryFailure }) {
  return (
    <p
      role="alert"
      className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
    >
      {messages[reason]}
    </p>
  );
}
