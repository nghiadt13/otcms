# Homepage OTCMS — kế hoạch khung (skeletal)

Trạng thái: **chờ duyệt** · Phạm vi: chỉ front-end, dữ liệu giả qua lớp API (như `roomApi`).

## Mục tiêu

- Khách (Guest) hiểu phòng khám làm gì → tin tưởng → **Đặt lịch khám** (cần tài khoản bệnh nhân, BR-09/BR-26).
- Cùng phong cách với màn xác thực: Teal + Coral, Be Vietnam Pro, chữ lớn (body `text-base`/`text-lg`), nút `h-12`, icon lucide.

## Căn cứ nghiệp vụ

| Khối                      | Căn cứ                  | Ghi chú                                      |
| ------------------------- | ----------------------- | -------------------------------------------- |
| Bác sĩ                    | BR-13, UC18–UC20        | Chỉ hồ sơ bác sĩ đã công khai                |
| Dịch vụ + giá             | BR-65, BR-67, UC69–UC72 | Chỉ dịch vụ đang hoạt động, giá công khai    |
| Đánh giá + phản hồi       | BR-60, UC62–UC63        | Phản hồi chỉ hiện khi đánh giá còn công khai |
| Hero, giới thiệu, liên hệ | BR-68, UC73–UC74        | Nội dung website do Admin đăng               |

## Bố cục (trên → dưới)

1. **Header dính**: logo · Dịch vụ · Bác sĩ · Quy trình · Đánh giá · Liên hệ · [Đăng nhập] [Đặt lịch khám]. Mobile: menu ☰ dạng sheet.
2. **Hero** 2 cột: pill coral, h1 (`text-4xl sm:text-5xl`), mô tả, 2 CTA, 3 thông tin nhanh (giờ mở cửa, hotline, địa chỉ); bên phải là minh họa (bản lớn của banner auth).
3. **Dải tin cậy**: 3–4 con số (bác sĩ, dịch vụ, lượt khám...). Lấy từ nội dung website, không tự bịa.
4. **Dịch vụ**: lưới 3×2 thẻ (icon, tên, mô tả, "Từ xxx đ"), kèm link "Xem tất cả".
5. **Đội ngũ bác sĩ**: 4 thẻ (ảnh/viết tắt tên, học hàm, chuyên sâu, số năm kinh nghiệm).
6. **Quy trình khám 01–04**: Đặt lịch → Check-in → Khám & chụp phim → Xem kết quả online (cùng kiểu đánh số với panel auth).
7. **Cổng bệnh nhân**: 3 lợi ích (dùng lại nội dung panel auth) + CTA "Tạo tài khoản" (nền `brand-soft`).
8. **Đánh giá**: 3 thẻ (sao, nội dung, tên viết tắt, phản hồi của phòng khám).
9. **Liên hệ + CTA cuối**: khối nền `bg-brand` gồm địa chỉ, giờ làm việc, hotline, CTA trắng; khung bản đồ để sau.
10. **Footer**: logo, link nhanh, © năm.

Nền các khối xen kẽ `canvas` / `white`; khung `max-w-6xl px-4 sm:px-6`; khoảng cách khối `py-16 sm:py-24`.

## Cấu trúc code dự kiến

```
src/app/(public)/page.tsx                 mỏng: lấy dữ liệu và ghép các section
src/components/brand/OtcmsLogo.tsx         chuyển từ auth, vì 2 feature cùng dùng (rào ESLint)
src/components/brand/ClinicIllustration.tsx  chuyển AuthBannerIllustration sang
src/features/home/
  api/homeContentApi.ts                   dữ liệu giả; sau này đổi sang API website/dịch vụ/bác sĩ
  types/homeContent.ts
  components/HomeHeader.tsx (+ HomeMobileNav.tsx, client)
  components/HomeHero.tsx, HomeStats.tsx, HomeServices.tsx, HomeDoctors.tsx,
             HomeVisitSteps.tsx, HomePatientPortal.tsx, HomeReviews.tsx,
             HomeContact.tsx, HomeFooter.tsx, HomeSection.tsx (khung tiêu đề chung)
```

- Mặc định là Server Component; chỉ menu mobile là client.
- Mỗi file dưới 200 dòng.

## Trợ năng và chất lượng

- Một h1, các section dùng h2 theo `aria-labelledby`; có skip link "Bỏ qua tới nội dung".
- Neo cuộn dùng `scroll-mt-20`; tôn trọng `motion-reduce`; không cuộn ngang ở 375px.
- Ảnh khai báo kích thước (CLS); có trạng thái trống (ví dụ chưa có bác sĩ công khai thì ẩn khối hoặc hiện thông báo).
- Test: render đủ section, link CTA đúng đích, menu mobile mở/đóng bằng bàn phím.

## Các bước (sau khi duyệt khung)

1. Chuyển logo và minh họa sang `components/brand`, sửa import ở auth.
2. Tạo types + API giả.
3. Dựng Header/Hero/Footer, duyệt nhanh trên trình duyệt.
4. Dựng các section còn lại.
5. Test, lint, build; xem ở 1440 và 375px; cập nhật `architecture.md` và `ui-guidelines.md`.

## Quyết định đã chốt (17/10/2026)

- **Toàn bộ dữ liệu là dữ liệu giả** (website, dịch vụ, bác sĩ, đánh giá), đặt sau lớp API giả để sau này thay bằng backend.
- CTA "Đặt lịch khám" của khách dẫn tới `/register`.
- **Có trang riêng `/doctors` và `/services`**. Trang chủ chỉ hiện dải 4 bác sĩ kèm nút "Xem tất cả" → `/doctors`; khối dịch vụ cũng có "Xem tất cả" → `/services`. Chưa làm trang chi tiết từng bác sĩ hay dịch vụ.
- Bỏ dải số liệu (khối 3) vì chưa có số thật.
- Thứ tự làm: Header + Hero + Footer → Dịch vụ + `/services` → Bác sĩ + `/doctors` → Quy trình, Cổng bệnh nhân, Đánh giá.

## Cấu trúc code (cập nhật)

- `src/app/(public)/(site)/layout.tsx`: Header + Footer dùng chung cho `/`, `/doctors`, `/services`; các màn auth không có header này.
- `features/clinic-website/`: nội dung website (BR-68), gồm Header, Hero, Footer.
- `features/clinic-services/`: danh mục dịch vụ (BR-65).
- `features/doctor-profiles/`: hồ sơ bác sĩ công khai (BR-13).
- Trang chủ ghép các feature ở `app/` (đúng rào ESLint).

## Câu hỏi mở (cũ)

- CTA "Đặt lịch khám" của khách dẫn tới đâu: `/register` hay `/login?next=/booking`?
- Có cần trang riêng cho chi tiết bác sĩ và dịch vụ (`/doctors/[id]`, `/services`) ngay bây giờ, hay chỉ làm khối trên trang chủ?
- Có đủ số liệu thật cho dải tin cậy chưa? Nếu không thì bỏ khối này.
- Ảnh bác sĩ và ảnh phòng khám thật: tạm dùng chữ viết tắt hay ảnh minh họa?
- Có cần FAQ và bản đồ nhúng không? Đề xuất để sau (YAGNI).
