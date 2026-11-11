package unimagdeportes.service.parameter;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.parameter.ParameterDtos.Rules;
import unimagdeportes.dto.parameter.ParameterDtos.UpdateParameters;
import unimagdeportes.mapper.parameter.ParameterMapper;
import unimagdeportes.repository.parameter.UsageParametersRepository;
import unimagdeportes.service.audit.AuditService;

@Service
public class ParameterService {
    private final UsageParametersRepository parameters;
    private final UsageRuleSettings settings;
    private final ParameterMapper mapper;
    private final AuditService audit;

    public ParameterService(UsageParametersRepository parameters, UsageRuleSettings settings,
            ParameterMapper mapper, AuditService audit) {
        this.parameters = parameters;
        this.settings = settings;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CurrentParameters current() {
        var values = parameters.findById(1).orElseThrow();
        return new CurrentParameters(values.getWeeklyQuotaHours(), values.getNoShowThreshold());
    }

    public record CurrentParameters(int weeklyQuotaHours, int noShowThreshold) { }

    @Transactional(readOnly = true)
    public Rules getRules() {
        return mapper.rules(current(), settings);
    }

    @Transactional(readOnly = true)
    public Rules getParameters() {
        return getRules();
    }

    @Transactional
    public Rules updateParameters(UpdateParameters update, UUID administratorId) {
        // Serialize partial updates so concurrent administrators preserve omitted values.
        var values = parameters.lockCurrent();
        values.update(update.quota() == null ? null : update.quota().weeklyHours(),
                update.sanctions() == null ? null : update.sanctions().noShowsForSuspension());
        // The shared audit identifier is UUID; the singleton is identified by its entity type.
        audit.record("PARAMETERS_UPDATED", "USAGE_PARAMETERS", null, administratorId);
        return mapper.rules(new CurrentParameters(values.getWeeklyQuotaHours(), values.getNoShowThreshold()), settings);
    }
}
