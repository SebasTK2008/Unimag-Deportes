package unimagdeportes.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.FinalText;
import org.openpdf.text.pdf.parser.ParsedText;
import org.openpdf.text.pdf.parser.ParsedTextImpl;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.openpdf.text.pdf.parser.TextAssembler;
import org.openpdf.text.pdf.parser.Word;

/** Checks the quantity next to an unwrapped item name in the definitive template, not arbitrary PDF digits. */
public final class PdfEquipmentAssertions {
    private PdfEquipmentAssertions() { }

    public static void assertItemQuantity(PdfReader pdf, String name, int quantity) throws IOException {
        var fragments = new ArrayList<Fragment>();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            int pageNumber = page;
            var collector = new EquipmentText();
            new PdfTextExtractor(pdf, collector).getTextFromPage(page);
            fragments.addAll(collector.fragments.stream()
                    .map(f -> new Fragment(pageNumber, f.text(), f.x(), f.y())).toList());
        }
        var names = fragments.stream().filter(f -> f.x() >= 107 && f.x() < 380 && f.text().equals(name)).toList();
        assertThat(names).as("Item name in the equipment body: %s", name).hasSize(1);
        var item = names.getFirst();
        var quantities = fragments.stream().filter(f -> f.page() == item.page()
                && f.x() >= 380 && f.x() < 540 && Math.abs(f.y() - item.y()) < 0.5f).map(Fragment::text).toList();
        assertThat(quantities).as("Quantity on the same row as %s", name)
                .containsExactly(Integer.toString(quantity));
    }

    private record Fragment(int page, String text, float x, float y) { }

    private static final class EquipmentText implements TextAssembler {
        private final List<Fragment> fragments = new ArrayList<>();

        private void collect(ParsedTextImpl text) {
            float x = text.getStartPoint().get(0), y = text.getStartPoint().get(1);
            if (y >= 305 && y <= 381 && !text.getText().isBlank()) {
                fragments.add(new Fragment(0, text.getText().strip(), x, y));
            }
        }

        @Override public void process(ParsedText text, String context) { collect(text); }
        @Override public void process(Word text, String context) { collect(text); }
        @Override public void renderText(ParsedTextImpl text) { collect(text); }
        @Override public void process(FinalText text, String context) { }
        @Override public void renderText(FinalText text) { }
        @Override public FinalText endParsingContext(String context) { return new FinalText(""); }
        @Override public String getWordId() { return ""; }
        @Override public void setPage(int page) { }
        @Override public void reset() { fragments.clear(); }
    }
}
