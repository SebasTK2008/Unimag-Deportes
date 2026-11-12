package unimagdeportes.mail;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.mail.Receipt;

@Component
public class ReceiptPdfGenerator {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")
            .withZone(ZoneId.of("America/Bogota"));

    public byte[] generate(Receipt receipt) {
        var output = new ByteArrayOutputStream();
        var document = new Document();
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Comprobante de reserva"));
            document.add(new Paragraph("Usuario: " + receipt.userName()));
            document.add(new Paragraph("Espacio: " + receipt.spaceName()));
            document.add(new Paragraph("Inicio: " + DATE_TIME.format(receipt.startAt())));
            document.add(new Paragraph("Fin: " + DATE_TIME.format(receipt.endAt())));
            document.add(new Paragraph("Implementos:"));
            if (receipt.equipment().isEmpty()) {
                document.add(new Paragraph("Sin implementos"));
            } else {
                for (var item : receipt.equipment()) {
                    document.add(new Paragraph(item.name() + " - cantidad: " + item.quantity()));
                }
            }
            document.add(new Paragraph("Reglas de cancelación: " + receipt.cancellationRules()));
            if (receipt.contactUrl() != null && !receipt.contactUrl().isBlank()) {
                document.add(new Paragraph("Contacto: " + receipt.contactUrl()));
            }
        } catch (DocumentException ex) {
            throw new IllegalStateException("No fue posible generar el comprobante PDF", ex);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }
}
