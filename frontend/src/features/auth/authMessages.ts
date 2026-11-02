// Nội dung thông báo của UC01, UC02, UC06. Chép đúng từ `docs/appendix-rules.md`
// (mục 4.2); sửa ở đó trước rồi mới sửa ở đây. Thông báo lỗi cố ý chung chung để
// không lộ tài khoản có tồn tại hay không (BR-03).
export const authMessages = {
  "MSG-01":
    "Không thể đăng nhập bằng thông tin này. Vui lòng kiểm tra và thử lại.",
  "MSG-02":
    "Nếu email này thuộc tài khoản có thể khôi phục, hướng dẫn đặt lại mật khẩu sẽ được gửi.",
  "MSG-03": "Mật khẩu đã được cập nhật. Vui lòng đăng nhập bằng mật khẩu mới.",
  "MSG-04":
    "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.",
  "MSG-08":
    "Không thể hoàn tất yêu cầu với thông tin hiện tại. Vui lòng kiểm tra và thử lại.",
  "MSG-09": "Nếu thông tin đăng ký hợp lệ, hướng dẫn xác minh sẽ được gửi.",
  "MSG-10": "Tài khoản bệnh nhân đã được xác minh.",
} as const;

export type AuthMessageCode = keyof typeof authMessages;
