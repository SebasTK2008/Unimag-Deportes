package unimagdeportes.mail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.awt.Color;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.mail.Receipt;

@Component
public class ReceiptPdfGenerator {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withZone(BOGOTA);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(BOGOTA);
    // Coordinates belong to the supplied, unmodified 612 × 792 pt template.
    private static final Rectangle EQUIPMENT_BODY = new Rectangle(72.744f, 302.09f, 540.1f, 384.67f);
    private static final Rectangle EQUIPMENT_CONTENT = new Rectangle(77, 305, 535, 381);
    private final byte[] template = loadTemplate();

    private static byte[] loadTemplate() {
        try (var input = new ClassPathResource("pdf/comprobante-definitivo.pdf").getInputStream()) {
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible cargar la plantilla del comprobante PDF", ex);
        }
    }

    public byte[] generate(Receipt receipt) {
        var output = new ByteArrayOutputStream();
        var document = new Document();
        PdfReader source = null;
        try {
            source = new PdfReader(template);
            document.setPageSize(source.getPageSize(1));
            var writer = PdfWriter.getInstance(document, output);
            document.open();
            var background = writer.getImportedPage(source, 1);
            var items = new ColumnText(null);
            items.addElement(equipmentTable(receipt));
            int status;
            do {
                var canvas = writer.getDirectContent();
                canvas.addTemplate(background, 0, 0);
                fillReservation(canvas, receipt);
                // Only the equipment body is merged; borders, header and all other artwork remain unchanged.
                canvas.saveState();
                canvas.setColorFill(Color.WHITE);
                canvas.rectangle(EQUIPMENT_BODY.getLeft(), EQUIPMENT_BODY.getBottom(),
                        EQUIPMENT_BODY.getWidth(), EQUIPMENT_BODY.getHeight());
                canvas.fill();
                canvas.restoreState();
                items.setCanvas(canvas);
                items.setSimpleColumn(EQUIPMENT_CONTENT.getLeft(), EQUIPMENT_CONTENT.getBottom(),
                        EQUIPMENT_CONTENT.getRight(), EQUIPMENT_CONTENT.getTop());
                status = items.go();
                if (ColumnText.hasMoreText(status) && items.getYLine() >= EQUIPMENT_CONTENT.getTop()) {
                    throw new DocumentException("La lista de implementos no cabe en la plantilla");
                }
                if (ColumnText.hasMoreText(status)) document.newPage();
            } while (ColumnText.hasMoreText(status));
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("No fue posible generar el comprobante PDF", ex);
        } finally {
            document.close();
            if (source != null) source.close();
        }
        return output.toByteArray();
    }

    private void fillReservation(PdfContentByte canvas, Receipt receipt) throws DocumentException {
        fillCell(canvas, receipt.userName(), 576.34f);
        fillCell(canvas, receipt.recipient(), 549.58f);
        fillCell(canvas, receipt.spaceName(), 522.79f);
        fillCell(canvas, DATE.format(receipt.startAt()), 496.03f);
        fillCell(canvas, TIME.format(receipt.startAt()), 469.27f);
        fillCell(canvas, TIME.format(receipt.endAt()), 442.51f);
        fillCell(canvas, receipt.contactUrl() == null ? "" : receipt.contactUrl(), 235.49f);
    }

    private void fillCell(PdfContentByte canvas, String text, float bottom) throws DocumentException {
        var box = new Rectangle(228.5f, bottom + 2, 534, bottom + 24);
        for (float size = 11; size >= 6; size -= 0.25f) {
            var font = FontFactory.getFont(FontFactory.HELVETICA, size);
            var column = new ColumnText(canvas);
            column.setSimpleColumn(new Phrase(text, font), box.getLeft(), box.getBottom(), box.getRight(), box.getTop(),
                    size * 1.15f, Element.ALIGN_LEFT);
            var simulation = ColumnText.duplicate(column);
            if (!ColumnText.hasMoreText(simulation.go(true))) {
                column.go();
                return;
            }
        }
        throw new DocumentException("El texto no cabe de forma legible en la celda de la plantilla");
    }

    private PdfPTable equipmentTable(Receipt receipt) throws DocumentException {
        PdfPTable table = null;
        for (float size = 10; size >= 8; size -= 0.5f) {
            table = new PdfPTable(new float[] {30, 280, 148});
            table.setTotalWidth(EQUIPMENT_CONTENT.getWidth());
            table.setLockedWidth(true);
            table.setSplitRows(true);
            table.setSplitLate(false);
            var font = FontFactory.getFont(FontFactory.HELVETICA, size);
            if (receipt.equipment().isEmpty()) {
                var cell = itemCell("Sin implementos", font);
                cell.setColspan(3);
                table.addCell(cell);
            } else {
                for (int index = 0; index < receipt.equipment().size(); index++) {
                    var item = receipt.equipment().get(index);
                    table.addCell(itemCell(Integer.toString(index + 1), font));
                    table.addCell(itemCell(item.name(), font));
                    table.addCell(itemCell(Integer.toString(item.quantity()), font));
                }
            }
            if (table.getTotalHeight() <= EQUIPMENT_CONTENT.getHeight()) break;
        }
        return table;
    }

    private PdfPCell itemCell(String text, Font font) {
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1);
        cell.setLeading(0, 1.15f);
        return cell;
    }
}
