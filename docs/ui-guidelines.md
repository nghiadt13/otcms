# Quy tắc giao diện OTCMS

Quy tắc chung khi dựng màn hình mới để mọi khu (public, admin, sau này patient/staff) nhìn như một sản phẩm. Khi cần làm khác quy tắc, thống nhất với nhóm rồi sửa tài liệu này trong cùng thay đổi.

**Nguồn sự thật trong code** (tài liệu này giải thích, code là giá trị thật):

| Thứ                                      | File                                                       |
| ---------------------------------------- | ---------------------------------------------------------- |
| Mã màu, font                             | `frontend/src/styles/brand-palette.css`                    |
| Token shadcn (primary, muted, border...) | `frontend/src/styles/app-theme.css`                        |
| Nạp font                                 | `frontend/src/lib/fonts.ts`                                |
| Component nền                            | `frontend/src/components/ui/*` (shadcn, style `base-nova`) |
| Tiêu đề trang                            | `frontend/src/components/PageHeader.tsx`                   |
| Khung admin, menu                        | `frontend/src/features/admin-shell/`                       |

## 1. Màu sắc

Hướng: **Teal + Coral**. Teal là màu chính, mang cảm giác y tế, tin cậy. Coral là điểm nhấn ấm, dùng ít.

| Vai trò         | Token / utility                         | Giá trị   | Dùng cho                                        |
| --------------- | --------------------------------------- | --------- | ----------------------------------------------- |
| Chính           | `primary` (`brand` trong bảng màu)      | `#0F766E` | Nút chính, link, icon mục đang chọn             |
| Chính khi hover | `brand-hover`                           | `#115E59` | Hover nút chính ở khu public                    |
| Nền nhạt teal   | `secondary`, `accent`, `sidebar-accent` | `#E6F4F1` | Badge phụ, hover menu, mục đang chọn            |
| Điểm nhấn       | `coral`                                 | `#FB7185` | Chấm, badge số đếm, chi tiết trang trí          |
| Chữ màu coral   | `coral-strong`                          | `#BE123C` | Chữ nhấn trên nền sáng                          |
| Nền coral nhạt  | `coral-soft`                            | `#FFF1F2` | Nhãn nhỏ (pill)                                 |
| Nền trang       | `background` / `canvas`                 | `#F5FAF9` | Nền toàn trang; thẻ (`card`) trắng nổi lên trên |
| Chữ phụ         | `muted-foreground`                      | `#5B6B6A` | Mô tả, chú thích                                |
| Lỗi, xóa        | `destructive`                           | `#DC2626` | Thông báo lỗi, nút xóa                          |
| Sao đánh giá    | `rating`                                | `#D97706` | Icon sao (3.2:1 trên trắng); không dùng cho chữ |

**Bắt buộc:**

- **Khu admin chỉ dùng token ngữ nghĩa** (`bg-primary`, `text-muted-foreground`, `border-border`...). Không viết màu Tailwind thô (`bg-teal-700`, `text-slate-500`) hay mã hex trong component. Cần màu mới thì thêm token, không thêm hex rải rác.
- **Khu public** cũng nạp theme shadcn (`app-theme.css`) và dùng component trong `components/ui` (Button, Card, Badge, Input, Label, Alert, Checkbox, NativeSelect, Sheet). Ưu tiên token ngữ nghĩa như khu admin; `brand-*` và `coral-*` chỉ dùng cho chi tiết thương hiệu chưa có token (nền teal của footer/panel, pill coral, `hover:bg-brand-hover`). Nút ở khu public dùng `publicButtonClass` trong `components/brand/publicButtonStyles.ts` (dựng trên `buttonVariants`, cao h-12/h-14).
- **Không đặt chữ trắng lên nền coral** (tương phản 2.7:1, không đạt). Chữ trên nền coral dùng `text-coral-ink`.
- **Coral không bao giờ mang nghĩa lỗi.** Lỗi và thao tác nguy hiểm dùng `destructive`. Không dùng coral cho thanh chỉ báo mục đang chọn hay đường viền lớn: thử rồi, trông lạc tông.
- Mọi cặp chữ/nền phải đạt tương phản ≥ 4.5:1 (chữ thường) hoặc ≥ 3:1 (chữ ≥ 18px đậm, icon). Kiểm bằng công cụ contrast trước khi thêm cặp màu mới.
- Cảnh báo (chưa nghiêm trọng) tạm dùng thang `amber` như ở `/workspace`; khi màn admin đầu tiên cần thì thêm token `warning`.
- Có sẵn token dark mode trong `.dark`, nhưng app chưa có nút bật. Màn mới vẫn dùng token để dark mode tự đúng.

## 2. Chữ

Font duy nhất: **Be Vietnam Pro** (thiết kế cho tiếng Việt, đủ dấu), nạp qua `next/font` ở mọi root layout. Không thêm font khác.

| Cấp               | Class                                               | Dùng cho                                         |
| ----------------- | --------------------------------------------------- | ------------------------------------------------ |
| Tiêu đề trang     | `text-2xl font-semibold tracking-tight`             | `h1` trong `PageHeader`; mỗi trang đúng một `h1` |
| Tiêu đề khối      | `text-lg font-semibold`                             | Tiêu đề thẻ, dialog                              |
| Nhãn nhóm         | `text-sm font-medium text-muted-foreground`         | Tiêu đề nhóm trên trang tổng quan                |
| Nội dung          | `text-sm`                                           | Bảng, form, menu, mô tả (mặc định của admin)     |
| Chú thích         | `text-xs text-muted-foreground`                     | Dòng phụ dưới tên, thời gian                     |
| Hero (chỉ public) | `text-4xl sm:text-5xl font-semibold tracking-tight` | Trang chủ                                        |

**Màn xác thực công khai** (đăng nhập, đăng ký, đặt lại mật khẩu; người dùng gồm cả người lớn tuổi) dùng chữ lớn hơn admin một bậc: tiêu đề thẻ `text-3xl`, nhãn/mô tả/nút/thông báo `text-base`, gợi ý và lỗi dưới ô `text-sm`, ô nhập và nút cao `h-12`. Component chung ở `src/features/auth/components` (`AuthCard`, `AuthTextField`...), dựng trên component shadcn và phóng to bằng `className`. Viền ô nhập dùng `border-muted-foreground` vì token `input` quá nhạt (~1.4:1) so với ngưỡng 3:1 của WCAG.

- Độ đậm chỉ dùng 400, 500 (`font-medium`), 600 (`font-semibold`), 700 (`font-bold`, hiếm).
- Không dùng cỡ tùy ý `text-[13px]`. Ngoại lệ đã có: `text-[11px]` cho nhãn nhóm menu, `text-[10px]` cho số đếm trên chuông.
- Văn bản giao diện viết tiếng Việt, câu ngắn, viết hoa chữ đầu câu (không Viết Hoa Mọi Chữ). Nút là động từ: "Thêm phòng", "Lưu", "Hủy".

## 3. Khoảng cách và bố cục

Dùng thang 4px của Tailwind (`1` = 4px). Không dùng giá trị lẻ kiểu `p-[13px]`.

| Chỗ                          | Giá trị                                                            |
| ---------------------------- | ------------------------------------------------------------------ |
| Lề nội dung trang admin      | `p-4 md:p-6` (đã có ở layout)                                      |
| `PageHeader` → nội dung      | `mb-6` (có sẵn trong component)                                    |
| Thanh lọc → bảng             | `mb-4`, các ô lọc cách nhau `gap-3`                                |
| Giữa các khối lớn trên trang | `space-y-8`                                                        |
| Lưới thẻ                     | `grid gap-4 sm:grid-cols-2 xl:grid-cols-3`                         |
| Giữa các mục menu con        | `gap-1` (để nền màu không dính nhau)                               |
| Trong form                   | Giữa các trường `space-y-4`/`5`, nhãn → ô nhập `mt-2` hoặc `gap-2` |

**Bố cục một màn admin chuẩn** (mẫu: `features/rooms/components/RoomManagement.tsx`):

1. `PageHeader` với `title`, `description` (một câu nói màn này làm gì) và `actions` (nút chính, đặt bên phải).
2. Thanh tìm kiếm/lọc.
3. Nội dung chính: bảng trong khung `rounded-lg border`, hoặc lưới `Card`.
4. Thêm/sửa dùng `Dialog`; xác nhận thao tác không hoàn tác dùng `AlertDialog`.

- **Tránh khuôn "template"** (audit 2026-10-17): không đặt nhãn/kicker hay pill phía trên tiêu đề; không dựng khối bằng lưới thẻ giống hệt nhau kiểu ô icon + tiêu đề + mô tả (dùng danh sách có vạch chia, thanh liền khối hoặc bảng); không đánh số 01/02/03 khi nội dung không phải trình tự; không rải chấm coral trang trí; không dùng minh họa SVG ghép từ hình cơ bản khi đã có ảnh thật; không hiện mã UC cho người dùng cuối (trừ trang chờ đang dựng).
- Một màn chỉ có **một** nút `default` (màu chính). Thao tác khác dùng `outline`, `ghost` hoặc `link`.
- Bo góc theo token `--radius` (`rounded-md`, `rounded-lg`); không tự đặt bán kính khác.
- Phần tử có nền màu phải nằm trọn trong khung cha, đừng để `overflow-hidden` cắt mất góc bo.
- Phải dùng được ở chiều rộng 375px: thanh lọc `flex-wrap`, lưới về 1 cột, bảng rộng cho cuộn ngang.

## 4. Component

- Dùng component trong `components/ui` trước. Thiếu thì thêm bằng `npx shadcn@latest add <tên>` (bỏ dấu `^` trong `package.json` theo `dependencies.md`). Không tự dựng lại nút, ô nhập, dialog bằng thẻ HTML thô ở khu admin.
- Sửa giao diện chung thì sửa token hoặc class truyền vào; hạn chế sửa tay file trong `components/ui`.
- Ảnh: ảnh trong `frontend/public/images/home/` là ảnh mẫu lấy từ Unsplash (giấy phép Unsplash, cùng một bộ ảnh của tác giả Vitaly Gariev để đồng bộ ánh sáng). Thay bằng ảnh thật của phòng khám trước khi phát hành; hiển thị qua `next/image`, luôn có `alt` (ảnh nền trang trí dùng `alt=""`).
- Icon: chỉ dùng `lucide-react`. Cỡ mặc định theo component (16px trong nút/menu); icon trang trí lớn tối đa `size-10`. Nút chỉ có icon phải có `aria-label`.
- Thêm màn admin: khai báo một mục trong `adminNavigation.ts` (tiêu đề, icon, mô tả, UC) rồi tạo `page.tsx`. Sidebar, header và trang tổng quan tự lấy từ đó; không viết tiêu đề trùng lặp ở chỗ khác.

## 5. Trạng thái của màn hình

Mỗi màn có dữ liệu phải xử lý đủ bốn trạng thái:

| Trạng thái | Cách hiển thị                                                                          |
| ---------- | -------------------------------------------------------------------------------------- |
| Đang tải   | `Skeleton` có hình dạng gần giống nội dung thật, không dùng chữ "Đang tải..." cho bảng |
| Trống      | Một dòng giải thích ngắn trong khung nội dung, kèm gợi ý thao tác nếu có               |
| Lỗi        | Chữ `text-destructive` nói rõ chuyện gì xảy ra, kèm nút "Thử lại" khi tải lại được     |
| Đang gửi   | Khóa nút gửi (`disabled`) và đổi nhãn ("Đang lưu...") để không bấm hai lần             |

## 6. Chuyển động

- Chuyển động để giúp người dùng hiểu thay đổi, không để trang trí. Thời lượng 150–300ms, easing kiểu ease-out.
- Chỉ animate `opacity`, `transform`, `height` của khối nhỏ. Không animate cả trang.
- Luôn kèm `motion-reduce:transition-none` (hoặc tương đương) cho người bật giảm chuyển động.

## 7. Khả năng truy cập

- Mọi ô nhập có nhãn (`Label` hoặc `aria-label`). Thông báo lỗi dùng `role="alert"`.
- Thứ tự tiêu đề đúng cấp (`h1` → `h2`), không nhảy cấp để lấy cỡ chữ.
- Không truyền đạt thông tin chỉ bằng màu: trạng thái luôn có chữ đi kèm (ví dụ `Switch` + "Sẵn sàng").
- Dùng được hoàn toàn bằng bàn phím, viền focus phải nhìn thấy (theme đã có `ring`).
- Phần bị thu gọn/ẩn không được nhận focus bằng Tab.

## 8. Kiểm tra trước khi merge một màn hình

- [ ] Không có màu Tailwind thô hay mã hex mới trong component admin (`grep -nE "(slate|gray|teal|rose|red)-[0-9]|#[0-9a-fA-F]{6}" src/features`)
- [ ] Có `PageHeader`, đúng một `h1`, đúng một nút chính
- [ ] Đủ 4 trạng thái: đang tải, trống, lỗi, đang gửi
- [ ] Xem ở 375px và ở desktop, không tràn ngang ngoài bảng
- [ ] Đi hết màn bằng bàn phím, focus nhìn thấy được
- [ ] `npm run lint`, `npm test`, `npm run build` đều qua
