/** Gốc URL của backend Spring Boot; dùng chung cho API nghiệp vụ và API xác thực. */
export const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
