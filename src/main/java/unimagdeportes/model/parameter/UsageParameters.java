package unimagdeportes.model.parameter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usage_parameters")
public class UsageParameters {
    @Id
    private Integer id;
    private int weeklyQuotaHours;
    private int noShowThreshold;

    protected UsageParameters() { }

    public int getWeeklyQuotaHours() { return weeklyQuotaHours; }
    public int getNoShowThreshold() { return noShowThreshold; }
}
