# Impeccable audit — Homepage, Auth (login/register/forgot/reset), Admin

Ngày: 2026-10-17 · Phạm vi: `otcms/frontend` · Nguồn chứng cứ: đọc code + detector (`impeccable detect` regex trên `src`, và URL scan `/`, `/login`, `/register`, `/admin`, `/admin/rooms`) + screenshot 1366px và 375px.

## Audit Health Score (trước khi sửa)

| #         | Dimension                | Score     | Key finding                                                                                                                  |
| --------- | ------------------------ | --------- | ---------------------------------------------------------------------------------------------------------------------------- |
| 1         | Accessibility            | 3         | Tốt: skip link, label, aria-describedby, focus ring, role=alert. Sao đánh giá `amber-500` trên trắng ~2.1:1 (< 3:1 non-text) |
| 2         | Performance              | 3         | `next/image`, `priority` hero, ít JS. `backdrop-blur` trang trí ở header + pill hero                                         |
| 3         | Theming                  | 3         | Token tốt; còn màu thô `text-amber-500` (reviews), `text-red-800` (AuthAlert)                                                |
| 4         | Responsive               | 3         | Không tràn ngang ở 375px. Quick-access 4 thẻ lớn xếp dọc đẩy nội dung chính xuống ~1.5 màn hình                              |
| 5         | Implementation integrity | 2         | Nhiều scaffold "AI slop" lặp lại (chi tiết dưới)                                                                             |
| **Total** |                          | **14/20** | **Good — điểm yếu là integrity/thẩm mỹ**                                                                                     |

## Integrity verdict

**Fail nhẹ.** Nền tảng (token, font, guideline) mạch lạc và đặc thù sản phẩm, nhưng bề mặt lắp từ các khuôn mặc định: thẻ icon-tile lặp 4 lần, eyebrow pill, số thứ tự 01/02/03 trang trí, minh họa SVG ghép hình cơ bản, CTA band teal, chấm coral rải rác. Trông "được dựng bởi template" hơn là phòng khám thật.

Detector: 0 anti-pattern tính điểm; 1 advisory xác nhận `shape-assembled-illustration` (SVG 27 hình cơ bản) trên `/login`, `/register`.

## Findings (P0–P3)

### Homepage

- **[P1] Icon-tile card grid lặp làm khung trang** — `HomeQuickAccess.tsx` (4 thẻ icon-trong-ô + tiêu đề + mô tả + mũi tên), `FeaturedServices.tsx` (ô icon `size-12 rounded-xl bg-secondary` mỗi dịch vụ), `HomeVisitSteps.tsx` (vòng tròn icon). Cùng một motif 3 khối liên tiếp → đơn điệu, "template". Mobile: quick-access chiếm ~1.5 màn hình. → `/impeccable distill`, `/impeccable layout`
- **[P1] Eyebrow pill kính mờ trên h1** — `HomeHero.tsx` badge `bg-white/15 backdrop-blur` + chấm coral. Kicker trên tiêu đề + glass trang trí. → `/impeccable quieter`
- **[P2] "Bước 01/02" + icon + vạch nối** — `HomeVisitSteps.tsx`: số đệm 0 kiểu template, icon thừa khi đã có số. Số thứ tự có nghĩa (quy trình) nên giữ, bỏ icon và padding.
- **[P2] CTA band teal bo góc ngay trên footer teal** — `HomeBookingCta.tsx`: khối màu lớn + 2 nút, khuôn "CTA banner" mặc định, lặp CTA lần thứ 4.
- **[P2] Hotline lặp sát nhau** — `SiteTopBar` và `SiteHeader` (xl) cùng hiện hotline cách nhau 44px.
- **[P2] Sao đánh giá màu thô, tương phản thấp** — `FeaturedReviews.tsx` `text-amber-500` (~2.1:1). Vi phạm guideline token + WCAG 1.4.11.
- **[P3] Chấm coral trang trí** — footer ©, panel auth ©, pill hero. Rải điểm nhấn không mang nghĩa.

### Auth

- **[P1] Minh họa SVG clip-art** — `components/brand/ClinicIllustration.tsx` (detector advisory). Trong khi repo đã có bộ ảnh thật đồng bộ ánh sáng. → thay ảnh thật.
- **[P2] Lợi ích đánh số 01/02/03 + icon tile** — `AuthBrandPanel.tsx`: không phải trình tự, số chỉ trang trí; 3 lớp tín hiệu (số + icon + tiêu đề).
- **[P2] Icon tile trên h1 + eyebrow "Dành cho bệnh nhân"** — `AuthCard.tsx`. Đăng ký 3 bước nhưng không có chỉ báo tiến trình; icon đổi theo bước là cách duy nhất báo bước → nên thay bằng thanh tiến trình có chữ.
- **[P3] `text-red-800` thô** trong `AuthAlert.tsx` (đã có chú thích lý do tương phản).

### Admin

- **[P1] Trang tổng quan = sidebar nhân bản thành 15 thẻ** — `AdminOverview.tsx`: lưới thẻ icon + badge giống hệt nhau, không thêm thông tin so với sidebar; kém scan.
- **[P1] Lộ mã nội bộ "UC07–UC09" cho người dùng** — badge ở `AdminOverview.tsx` (mã tạm, theo AGENTS.md có thể đổi). Placeholder vẫn giữ vì là màn tạm cho nhóm dev.
- **[P3] Logo sidebar "OT" khác logo public (xương)** — `AdminSidebar.tsx`: nhận diện không nhất quán.

### Cả 3 khu

- **[P3] Bề mặt trình duyệt mặc định** — selection, caret, accent-color chưa theo palette.

## Positive

- Token màu + guideline rõ ràng, contrast có ghi chú; font Be Vietnam Pro hợp tiếng Việt.
- A11y nền tốt (skip link, Sheet bẫy focus, aria-current, focus chuyển về tiêu đề khi đổi bước).
- Ảnh thật cùng bộ, `next/image` đúng `sizes`.
- Cỡ chữ/nút lớn cho người lớn tuổi ở khu public — quyết định có chủ đích.

## Kế hoạch sửa (thực hiện ngay)

1. Hero: bỏ eyebrow pill (và field `eyebrow` trong mock/type).
2. Quick access → một thanh 4 ô liền khối có vạch chia, icon nhỏ inline, không thẻ riêng; mobile 2×2 gọn.
3. Dịch vụ: bỏ icon tile; tên + giá cùng hàng, giá là điểm neo.
4. Quy trình: bỏ icon tròn; số 1–4 lớn làm marker, vạch trên mỗi bước, căn trái.
5. CTA cuối: bỏ khối teal; dải sáng có viền, h2 + 1 nút chính + hotline dạng số lớn.
6. Header: bỏ khối hotline trùng với top bar.
7. Sao đánh giá: token `rating` (amber-600, ≥3:1).
8. Auth panel: thay SVG bằng ảnh thật, bỏ số 01/02/03 và icon tile; bỏ chấm coral; xóa `ClinicIllustration.tsx`.
9. AuthCard: bỏ icon tile + eyebrow; thêm thanh tiến trình "Bước x/3" cho đăng ký.
10. Admin overview: mỗi nhóm một khung danh sách (icon + tên + mô tả), bỏ badge UC.
11. Sidebar dùng cùng mark xương với logo public.
12. Theme selection/caret/accent-color từ palette.

## Kết quả sau khi sửa

- Đã làm 12 mục trên. Detector URL (1366px + 375px) trên `/`, `/login`, `/register`, `/admin`: 0 finding, advisory SVG đã hết.
- Code review (subagent) → đã sửa thêm: hotline trong Sheet mobile (TopBar ẩn dưới lg), `aria-describedby` từ h1 tới "Bước x/3", sao rỗng `text-muted-foreground` (≥3:1), vạch CTA nằm trong khung, khoảng cách về thang 4px, helper `hotlineTelHref` dùng chung.
- `tsc`, `npm run lint`, `npm test` (43/43), `npm run build` đều qua.
- Không sửa: `AdminNavGroup.tsx` dùng `bg-brand`/`w-[3px]` (thay đổi có sẵn từ trước, không thuộc đợt này); `rounded-xl/2xl` ở khu public lệch với doc §3 (đã có từ trước).
- Ước lượng điểm sau sửa: A11y 3→4, Theming 3→4, Responsive 3→4, Integrity 2→3 ≈ 18/20.

## Unresolved questions

- Độ tương phản mô tả hero ở lg trên vùng ảnh sáng (gradient trong suốt từ 60%) chưa đo trên ảnh thật.
- Hotline/địa chỉ/giờ là dữ liệu giả — cần dữ liệu thật trước khi phát hành.
- Ảnh Unsplash vẫn cần thay bằng ảnh thật của phòng khám (đã ghi trong guideline).
- Logo hiện là icon lucide `Bone` — nên có logo thật do nhóm/thiết kế cung cấp.
