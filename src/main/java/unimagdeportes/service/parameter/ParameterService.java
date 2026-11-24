package unimagdeportes.service.parameter;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.parameter.ParameterDtos.Rules;
import unimagdeportes.dto.parameter.ParameterDtos.UpdateParameters;
import unimagdeportes.mapper.parameter.ParameterMapper;
import unimagdeportes.repository.parameter.UsageParametersRepository;
import unimagdeportes.repository.parameter.UsageParametersHeadRepository;
import unimagdeportes.service.audit.AuditService;

@Service
public class ParameterService {
    private final UsageParametersRepository parameters;
    private final UsageParametersHeadRepository heads;
    private final UsageRuleSettings settings;
    private final ParameterMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public ParameterService(UsageParametersRepository parameters, UsageParametersHeadRepository heads,
            UsageRuleSettings settings, ParameterMapper mapper, AuditService audit, Clock clock) {
        this.parameters = parameters;
        this.heads = heads;
        this.settings = settings;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentParameters current() {
        var values = parameters.current();
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
        // Lock the stable head before reading: the previously latest version may no longer be current.
        var head = heads.lockCurrent();
        var previous = parameters.findById(head.getCurrentVersionId()).orElseThrow();
        int quota = update.quota() == null || update.quota().weeklyHours() == null
                ? previous.getWeeklyQuotaHours() : update.quota().weeklyHours();
        int threshold = update.sanctions() == null || update.sanctions().noShowsForSuspension() == null
                ? previous.getNoShowThreshold() : update.sanctions().noShowsForSuspension();
        if (quota != previous.getWeeklyQuotaHours() || threshold != previous.getNoShowThreshold()) {
            var next = previous.nextVersion(quota, threshold, clock.instant(), administratorId);
            // Insert before changing the foreign key; both operations and the audit share this transaction.
            parameters.saveAndFlush(next);
            head.activate(next.getId());
            audit.record("PARAMETERS_UPDATED", "USAGE_PARAMETERS", next.getId(), administratorId);
        }
        return mapper.rules(new CurrentParameters(quota, threshold), settings);
    }
}
