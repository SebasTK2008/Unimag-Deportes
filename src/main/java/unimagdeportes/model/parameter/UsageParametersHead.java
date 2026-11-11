package unimagdeportes.model.parameter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "usage_parameters_head")
public class UsageParametersHead {
    @Id
    private Integer id;
    private UUID currentVersionId;

    protected UsageParametersHead() { }

    public UUID getCurrentVersionId() { return currentVersionId; }

    public void activate(UUID versionId) { currentVersionId = versionId; }
}
