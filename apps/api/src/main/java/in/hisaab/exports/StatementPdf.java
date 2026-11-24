package in.hisaab.exports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Draws a party statement as an A4 PDF. Pure: it gets finished strings (money and dates already
 * formatted) and returns bytes, so it knows nothing about Spring or the database.
 *
 * <p>One embedded font (Noto Sans) covers Latin and the rupee sign. PDFBox does no text shaping, so
 * a character the font cannot draw (Devanagari, emoji) is printed as "?" rather than failing.
 */
final class StatementPdf {

  /** Every cell is already text. Table rows are lists of cells in column order. */
  record Statement(
      String workspaceName,
      String partyName,
      String partyLine,
      String generatedOn,
      String totalPaid,
      String totalReceived,
      String netAmount,
      String netLabel,
      List<List<String>> dues,
      List<List<String>> transactions) {}

  private static final float MARGIN = 40;
  private static final float LINE = 14;
  private static final float BODY = 9;
  private static final float GRAY = 0.4f;

  private static final float TABLE_WIDTH = 515; // between the margins of an A4 page

  // Column widths. A right-aligned column is money: it is sized to its widest value, so an amount
  // is never cut. A width of 0 on any other column means "take what is left" (reason, details);
  // only that text is shortened with an ellipsis.
  private static final String[] DUE_HEAD = {
    "Reason", "Direction", "Due date", "Amount", "Paid", "Outstanding", "State"
  };
  private static final float[] DUE_WIDTH = {0, 70, 62, 0, 0, 0, 67};
  private static final boolean[] DUE_RIGHT = {false, false, false, true, true, true, false};

  private static final String[] TX_HEAD = {"Date", "Details", "Paid", "Received"};
  private static final float[] TX_WIDTH = {65, 0, 0, 0};
  private static final boolean[] TX_RIGHT = {false, false, true, true};

  static byte[] render(Statement s) {
    try (var doc = new PDDocument()) {
      PDFont font;
      try (var in = StatementPdf.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
        font = PDType0Font.load(doc, in);
      }
      try (var page = new Page(doc, font)) {
        page.write(s);
      }
      var out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The page being drawn on. Moves down as it writes and starts a new page when it runs out. */
  private static final class Page implements AutoCloseable {
    private final PDDocument doc;
    private final PDFont font;
    private PDPageContentStream cs;
    private float y;

    Page(PDDocument doc, PDFont font) throws IOException {
      this.doc = doc;
      this.font = font;
      newPage();
    }

    void write(Statement s) throws IOException {
      text(fit(s.workspaceName(), TABLE_WIDTH, 10), MARGIN, y, 10, GRAY);
      y -= 24;
      text(fit("Statement for " + s.partyName(), TABLE_WIDTH, 18), MARGIN, y, 18, 0);
      y -= 18;
      if (!s.partyLine().isBlank()) {
        text(fit(s.partyLine(), TABLE_WIDTH, 10), MARGIN, y, 10, GRAY);
        y -= LINE;
      }
      text("Generated on " + s.generatedOn(), MARGIN, y, BODY, GRAY);
      y -= 30;

      summary(0, "Total paid", s.totalPaid(), null);
      summary(1, "Total received", s.totalReceived(), null);
      summary(2, "Net balance", s.netAmount(), s.netLabel());
      y -= 52;

      table("Dues", DUE_HEAD, DUE_WIDTH, DUE_RIGHT, s.dues(), "No dues with this party.");
      y -= 12;
      table(
          "Transactions",
          TX_HEAD,
          TX_WIDTH,
          TX_RIGHT,
          s.transactions(),
          "No transactions with this party.");
    }

    private void summary(int slot, String label, String value, String note) throws IOException {
      float x = MARGIN + slot * 175;
      text(label, x, y, BODY, GRAY);
      text(value, x, y - 18, 14, 0);
      if (note != null) {
        text(note, x, y - 32, BODY, GRAY);
      }
    }

    private void table(
        String title,
        String[] head,
        float[] preset,
        boolean[] right,
        List<List<String>> rows,
        String empty)
        throws IOException {
      room(3 * LINE);
      text(title, MARGIN, y, 12, 0);
      y -= LINE + 4;
      if (rows.isEmpty()) {
        text(empty, MARGIN, y, BODY, GRAY);
        y -= LINE;
        return;
      }
      var width = columnWidths(head, preset, right, rows);
      header(head, width, right);
      for (var row : rows) {
        if (room(LINE)) {
          header(head, width, right);
        }
        cells(row.toArray(new String[0]), width, right, 0);
        y -= LINE;
      }
    }

    /** Money columns fit their widest value; the one flexible column gets what is left (min 40). */
    private float[] columnWidths(
        String[] head, float[] preset, boolean[] right, List<List<String>> rows)
        throws IOException {
      var width = preset.clone();
      float used = 0;
      int flex = -1;
      for (int i = 0; i < width.length; i++) {
        if (right[i]) {
          float widest = width(head[i], BODY);
          for (var row : rows) {
            widest = Math.max(widest, width(safe(row.get(i)), BODY));
          }
          width[i] = widest + 12; // 6 pt of padding each side
        } else if (width[i] == 0) {
          flex = i;
        }
        used += width[i];
      }
      width[flex] = Math.max(40, TABLE_WIDTH - used);
      return width;
    }

    private void header(String[] head, float[] width, boolean[] right) throws IOException {
      cells(head, width, right, GRAY);
      cs.setStrokingColor(0.75f);
      cs.setLineWidth(0.5f);
      cs.moveTo(MARGIN, y - 4);
      cs.lineTo(MARGIN + 515, y - 4);
      cs.stroke();
      y -= LINE + 2;
    }

    private void cells(String[] cell, float[] width, boolean[] right, float gray)
        throws IOException {
      float x = MARGIN;
      for (int i = 0; i < cell.length; i++) {
        // 6 pt of padding keeps neighbouring columns from touching.
        var t = fit(cell[i], width[i] - 6, BODY);
        if (right[i]) {
          text(t, x + width[i] - 6 - width(t, BODY), y, BODY, gray);
        } else {
          text(t, x, y, BODY, gray);
        }
        x += width[i];
      }
    }

    /** Starts a new page when fewer than {@code needed} points are left. True if it did. */
    private boolean room(float needed) throws IOException {
      if (y - needed >= MARGIN) {
        return false;
      }
      newPage();
      return true;
    }

    private void newPage() throws IOException {
      if (cs != null) {
        cs.close();
      }
      var page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      cs = new PDPageContentStream(doc, page);
      y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void text(String t, float x, float baseline, float size, float gray)
        throws IOException {
      cs.beginText();
      cs.setFont(font, size);
      cs.setNonStrokingColor(gray);
      cs.newLineAtOffset(x, baseline);
      cs.showText(safe(t));
      cs.endText();
    }

    private float width(String t, float size) throws IOException {
      return font.getStringWidth(t) / 1000 * size;
    }

    /** Cuts the text to {@code max} points with an ellipsis. Money never reaches this cut. */
    private String fit(String t, float max, float size) throws IOException {
      t = safe(t);
      if (width(t, size) <= max) {
        return t;
      }
      while (!t.isEmpty() && width(t + "…", size) > max) {
        t = t.substring(0, t.length() - 1);
      }
      return t + "…";
    }

    /** Replaces control characters with a space and anything the font cannot draw with "?". */
    private String safe(String t) {
      var out = new StringBuilder();
      for (int i = 0; i < t.length(); ) {
        int cp = t.codePointAt(i);
        i += Character.charCount(cp);
        var ch = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
        try {
          font.encode(ch);
          out.append(ch);
        } catch (IOException | IllegalArgumentException e) {
          out.append('?');
        }
      }
      return out.toString();
    }

    @Override
    public void close() throws IOException {
      cs.close();
    }
  }

  private StatementPdf() {}
}
