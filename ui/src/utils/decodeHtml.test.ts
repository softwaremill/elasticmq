import decodeHtmlEntities from "./decodeHtml";

describe("decodeHtmlEntities", () => {
  test("should decode basic HTML entities", () => {
    const input = "&lt;div&gt;Text with &amp; special characters&lt;/div&gt;";
    const expected = "<div>Text with & special characters</div>";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });

  test("should decode numeric entities", () => {
    const input =
      "&#60;span&#62;Text with &#38; numeric entities&#60;/span&#62;";
    const expected = "<span>Text with & numeric entities</span>";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });

  test("should decode hexadecimal entities", () => {
    const input =
      "&#x3C;p&#x3E;Text with &#x26; hexadecimal entities&#x3C;/p&#x3E;";
    const expected = "<p>Text with & hexadecimal entities</p>";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });

  test("should handle text without HTML entities", () => {
    const input = "Text without HTML entities";
    const expected = "Text without HTML entities";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });

  test("should handle empty text", () => {
    const input = "";
    const expected = "";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });

  test("should decode entities for accented characters", () => {
    const input = "Text with accent&ccedil;&atilde;o";
    const expected = "Text with accentção";
    expect(decodeHtmlEntities(input)).toBe(expected);
  });
});
