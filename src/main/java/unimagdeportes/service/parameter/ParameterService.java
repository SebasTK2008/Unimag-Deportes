package unimagdeportes.service.parameter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.repository.parameter.UsageParametersRepository;

@Service
public class ParameterService {
    private final UsageParametersRepository parameters;

    public ParameterService(UsageParametersRepository parameters) {
        this.parameters = parameters;
    }

    @Transactional(readOnly = true)
    public CurrentParameters current() {
        var values = parameters.findById(1).orElseThrow();
        return new CurrentParameters(values.getWeeklyQuotaHours(), values.getNoShowThreshold());
    }

    public record CurrentParameters(int weeklyQuotaHours, int noShowThreshold) { }
}
