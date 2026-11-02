import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { insertBold, insertLink, insertList } from "../schema/markdownInsert";
import { WebsiteContentBody } from "./WebsiteContentBody";

describe("WebsiteContentBody", () => {
  it("**chữ** thành <strong>, danh sách thành <li>, đoạn tách bằng dòng trống", () => {
    const { container } = render(
      <WebsiteContentBody
        markdown={"Đoạn **đậm** một.\n\nĐoạn hai.\n\n- a\n- b\n\n1. x"}
      />,
    );
    expect(container.querySelector("strong")).toHaveTextContent("đậm");
    expect(container.querySelectorAll("p")).toHaveLength(2);
    expect(container.querySelectorAll("ul li")).toHaveLength(2);
    expect(container.querySelectorAll("ol li")).toHaveLength(1);
  });

  it("<script> thô chỉ là chữ, không thành phần tử", () => {
    const { container } = render(
      <WebsiteContentBody
        markdown={'<script>alert(1)</script> <img src=x onerror="alert(1)">'}
      />,
    );
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(container).toHaveTextContent("<script>alert(1)</script>");
  });

  it("chặn javascript: và data:, link ngoài có rel đúng", () => {
    render(
      <WebsiteContentBody
        markdown={
          "[bẫy](javascript:alert(1)) [dữ liệu](data:text/html,x) [ngoài](https://example.com)"
        }
      />,
    );
    expect(screen.queryByRole("link", { name: "bẫy" })).toBeNull();
    expect(screen.queryByRole("link", { name: "dữ liệu" })).toBeNull();
    const external = screen.getByRole("link", { name: "ngoài" });
    expect(external).toHaveAttribute("href", "https://example.com");
    expect(external).toHaveAttribute("rel", "noopener noreferrer");
    expect(external).toHaveAttribute("target", "_blank");
  });

  it("ảnh https hiện, ảnh javascript: bị bỏ", () => {
    const { container } = render(
      <WebsiteContentBody
        markdown={"![ok](/media/a.jpg) ![xấu](javascript:x)"}
      />,
    );
    expect(container.querySelectorAll("img")).toHaveLength(1);
  });
});

describe("markdownInsert", () => {
  it("in đậm bọc vùng chọn; danh sách thêm '- '; liên kết chọn sẵn phần url", () => {
    expect(insertBold({ text: "abc", start: 1, end: 2 }).text).toBe("a**b**c");
    expect(insertList({ text: "một\nhai", start: 0, end: 7 }).text).toBe(
      "- một\n- hai",
    );
    const link = insertLink({ text: "xem", start: 0, end: 3 });
    expect(link.text).toBe("[xem](https://)");
    expect(link.text.slice(link.selectionStart, link.selectionEnd)).toBe(
      "https://",
    );
  });
});
