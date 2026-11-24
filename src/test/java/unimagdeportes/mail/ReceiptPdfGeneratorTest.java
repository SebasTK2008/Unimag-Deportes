package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static unimagdeportes.support.PdfEquipmentAssertions.assertItemQuantity;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            assertThat(text).contains("Persona", "user@unimagdalena.edu.co", "Cancha", "11/11/2026", "10:00", "11:00",
                    "Balón", "https://client.example/contact", "Firma del administrador");
            assertThat(text).doesNotContain("Regla de cancelación", "Este comprobante no sustituye el check-in");
            assertItemQuantity(pdf, "Balón", 2);
            assertThat(pdf.getPageSize(1).getWidth()).isEqualTo(612);
            assertThat(pdf.getPageSize(1).getHeight()).isEqualTo(792);
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

    @Test
    void preservesTheOriginalTemplateBytes() throws Exception {
        try (var input = getClass().getResourceAsStream("/pdf/comprobante-definitivo.pdf")) {
            assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.readAllBytes())))
                    .isEqualTo("30e5c02c2fa2310b4c722a13f0501041ee412c131ba18d3887378dd32ea2679c");
        }
    }

    @Test
    void equipmentAssertionRejectsSwappedQuantitiesEvenWhenBothNumbersExistInThePdf() throws Exception {
        var receipt = new Receipt("test@unimagdalena.edu.co", "Persona", "Cancha",
                Instant.parse("2026-11-25T19:00:00Z"), Instant.parse("2026-11-25T20:00:00Z"),
                List.of(new Receipt.EquipmentItem("Balones de prueba", 47),
                        new Receipt.EquipmentItem("Conos de prueba", 23)), "Reglas", "");
        var pdf = new PdfReader(new ReceiptPdfGenerator().generate(receipt));
        try {
            assertItemQuantity(pdf, "Balones de prueba", 47);
            assertItemQuantity(pdf, "Conos de prueba", 23);
            assertThatThrownBy(() -> assertItemQuantity(pdf, "Balones de prueba", 23))
                    .isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> assertItemQuantity(pdf, "Conos de prueba", 47))
                    .isInstanceOf(AssertionError.class);
        } finally {
            pdf.close();
        }
    }

    @Test
    void includesEveryEquipmentItemAcrossContinuationPagesWithoutTruncation() throws Exception {
        var items = new ArrayList<Receipt.EquipmentItem>();
        for (int i = 1; i <= 35; i++) {
            items.add(new Receipt.EquipmentItem("Implemento de prueba número " + i, i));
        }
        var receipt = new Receipt("prueba@unimagdalena.edu.co", "Persona de prueba", "Cancha de prueba",
                Instant.parse("2026-11-25T19:00:00Z"), Instant.parse("2026-11-25T21:00:00Z"),
                items, "Reglas solo en el correo", "https://client.example/contact");
        byte[] bytes = new ReceiptPdfGenerator().generate(receipt);
        var pdf = new PdfReader(bytes);
        try {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                String content = new PdfTextExtractor(pdf).getTextFromPage(page);
                assertThat(content).contains("Persona de prueba", "COMPROBANTE DE RESERVA CONFIRMADA");
                text.append(content);
            }
            for (int i = 1; i <= 35; i++) assertThat(text.toString()).contains("Implemento de prueba número " + i);
        } finally {
            pdf.close();
        }
        Files.createDirectories(Path.of("target/test-artifacts"));
        Files.write(Path.of("target/test-artifacts/receipt-many-items.pdf"), bytes);
    }

    @Test
    void writesAPreviewUsingTheRealGeneratorAndFitsLongValues() throws Exception {
        var receipt = new Receipt("prueba.visual@unimagdalena.edu.co", "Persona de prueba de Unimag Deportes",
                "Cancha múltiple de la Universidad del Magdalena",
                Instant.parse("2026-11-25T19:00:00Z"), Instant.parse("2026-11-25T21:00:00Z"),
                List.of(new Receipt.EquipmentItem("Balón de baloncesto", 2),
                        new Receipt.EquipmentItem("Conos deportivos", 10),
                        new Receipt.EquipmentItem("Chalecos de entrenamiento", 8),
                        new Receipt.EquipmentItem("Balón de voleibol", 1),
                        new Receipt.EquipmentItem("Cuerda para entrenamiento", 3)),
                "Reglas solo en el correo", "https://client.example/app/contacto");
        Files.createDirectories(Path.of("target/test-artifacts"));
        Files.write(Path.of("target/test-artifacts/receipt-preview.pdf"), new ReceiptPdfGenerator().generate(receipt));
    }
}
