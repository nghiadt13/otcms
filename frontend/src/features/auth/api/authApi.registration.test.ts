import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthRequestError } from "../types/auth";
import {
  registerPatient,
  resendRegistrationCode,
  verifyRegistrationEmail,
} from "./authApi";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const input = {
  fullName: "  Nguyễn Văn An ",
  email: " an@example.com ",
  password: "matkhau123",
  confirmPassword: "matkhau123",
  consent: true,
};

describe("authApi: đăng ký qua backend", () => {
  it("gửi tên và email đã cắt khoảng trắng, không gửi ngày sinh, giới tính, SĐT", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    await registerPatient(input);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/patient\/register$/);
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({
      fullName: "Nguyễn Văn An",
      email: "an@example.com",
      password: "matkhau123",
      confirmPassword: "matkhau123",
      patientConsent: true,
    });
  });

  it("không gửi CSRF hay thông tin phiên (backend miễn CSRF cho ba endpoint này)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await verifyRegistrationEmail(" an@example.com ", "123456");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/patient\/verify-email$/);
    expect(JSON.parse(String(init?.body))).toEqual({
      email: "an@example.com",
      otp: "123456",
    });
    const headers = new Headers(init?.headers);
    expect(headers.has("X-CSRF-TOKEN")).toBe(false);
    expect(headers.has("Authorization")).toBe(false);
  });

  it("gửi lại mã gọi resend-otp với email đã cắt khoảng trắng", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    await resendRegistrationCode(" an@example.com ");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/patient\/resend-otp$/);
    expect(JSON.parse(String(init?.body))).toEqual({ email: "an@example.com" });
  });

  it.each([
    [400, "MSG-08"],
    [429, "MSG-04"],
    [503, "MSG-04"],
    [500, "MSG-04"],
  ])("backend trả %i thì báo %s", async (status, code) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    await expect(registerPatient(input)).rejects.toMatchObject({
      name: AuthRequestError.name,
      code,
    });
  });

  it("mất mạng thì báo MSG-04", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(verifyRegistrationEmail("an@example.com", "123456"))
      .rejects.toMatchObject({ code: "MSG-04" });
  });
});
