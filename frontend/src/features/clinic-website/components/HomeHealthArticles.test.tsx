import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { HomeHealthArticles } from "./HomeHealthArticles";
it("shows links and images from published homepage data and escapes article titles", () => {
  render(
    <HomeHealthArticles
      articles={[
        {
          id: "a",
          title: "<script>Health</script>",
          summary: "Information",
          imageUrl: "http://localhost:8080/api/v1/public/website-images/i",
          publishedAt: "2026-11-01T02:00:00Z",
        },
      ]}
    />,
  );
  expect(
    screen.getByRole("link", { name: "<script>Health</script>" }),
  ).toHaveAttribute("href", "/bai-viet/a");
  expect(document.querySelector("script")).toBeNull();
  expect(document.querySelector("img")).toHaveAttribute(
    "src",
    "http://localhost:8080/api/v1/public/website-images/i",
  );
});
