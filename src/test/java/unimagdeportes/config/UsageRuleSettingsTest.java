package unimagdeportes.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsageRuleSettingsTest {
    @Test
    void permitsZeroMinuteRulesButRequiresPositiveSuspension() {
        assertThatCode(() -> new UsageRuleSettings(0, 0, 0, 0, 1)).doesNotThrowAnyException();
        for (int index = 0; index < 5; index++) {
            int[] values = {10, 60, 0, 10, 7};
            values[index] = index == 4 ? 0 : -1;
            assertThatThrownBy(() -> new UsageRuleSettings(values[0], values[1], values[2], values[3], values[4]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
