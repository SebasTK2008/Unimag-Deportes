package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import unimagdeportes.dto.mail.Receipt;

class ReceiptPdfGeneratorTest {
    @Test
    void includesReservationAndEquipmentInBogotaTime() throws Exception {
        var receipt = new Receipt("user@unimagdalena.edu.co", "Persona", "Cancha",
                Instant.parse("2026-11-11T15:00:00Z"), Instant.parse("2026-11-11T16:00:00Z"),
                List.of(new Receipt.EquipmentItem("Balón", 2)), "Regla de cancelación", "https://client.example/contact");
        var pdf = new PdfReader(new ReceiptPdfGenerator().generate(receipt));
        try {
            String text = new PdfTextExtractor(pdf).getTextFromPage(1);
            assertThat(text).contains("Persona", "Cancha", "11/11/2026 10:00", "11/11/2026 11:00",
                    "Balón", "cantidad: 2", "Regla de cancelación", "https://client.example/contact");
        } finally {
            pdf.close();
        }
    }

    @Test
    void identifiesReservationWithoutEquipment() throws Exception {
        var receipt = new Receipt("user@unimagdalena.edu.co", "Persona", "Cancha",
                Instant.parse("2026-11-11T15:00:00Z"), Instant.parse("2026-11-11T16:00:00Z"),
                List.of(), "Reglas", "");
        var pdf = new PdfReader(new ReceiptPdfGenerator().generate(receipt));
        try {
            assertThat(new PdfTextExtractor(pdf).getTextFromPage(1)).contains("Sin implementos");
        } finally {
            pdf.close();
        }
    }
}
