import { describe, expect, it } from "vitest";
import { attachmentError, attachmentType, MAX_ATTACHMENT_BYTES } from "./attachments";

const file = (name: string, type: string, size = 100) => ({ name, type, size });

describe("attachmentType", () => {
  it("accepts the allowed types, ignoring case", () => {
    expect(attachmentType(file("a.jpg", "image/jpeg"))).toBe("image/jpeg");
    expect(attachmentType(file("a.pdf", "Application/PDF"))).toBe("application/pdf");
  });

  it("treats an untyped .heic file as HEIC, but nothing else untyped", () => {
    expect(attachmentType(file("IMG_1.HEIC", ""))).toBe("image/heic");
    expect(attachmentType(file("notes", ""))).toBeNull();
  });

  it("rejects other types", () => {
    expect(attachmentType(file("a.txt", "text/plain"))).toBeNull();
    expect(attachmentType(file("a.html", "text/html"))).toBeNull();
  });
});

describe("attachmentError", () => {
  it("allows a normal file and exactly the size limit", () => {
    expect(attachmentError(file("a.png", "image/png"))).toBeNull();
    expect(attachmentError(file("a.pdf", "application/pdf", MAX_ATTACHMENT_BYTES))).toBeNull();
  });

  it("explains wrong type, empty and too big", () => {
    expect(attachmentError(file("a.txt", "text/plain"))).toMatch(/JPEG, PNG/);
    expect(attachmentError(file("a.pdf", "application/pdf", 0))).toMatch(/empty/);
    expect(attachmentError(file("a.pdf", "application/pdf", MAX_ATTACHMENT_BYTES + 1))).toMatch(
      /10 MB/,
    );
  });
});
