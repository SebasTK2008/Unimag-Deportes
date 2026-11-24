package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeekCalculatorTest {
    private final WeekCalculator calculator = new WeekCalculator();

    @Test
    void calculatesWeekInBogotaAcrossYearBoundary() {
        var date = LocalDate.of(2027, 1, 1);
        assertThat(calculator.startOfWeek(date).toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(calculator.nextWeek(date).toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(calculator.startOfWeek(date).getOffset().getTotalSeconds()).isEqualTo(-18000);
    }
}
