package unimagdeportes.repository.parameter;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import unimagdeportes.model.parameter.UsageParameters;

public interface UsageParametersRepository extends JpaRepository<UsageParameters, UUID> {
    @Query("select parameters from UsageParameters parameters, UsageParametersHead head "
            + "where head.id = 1 and parameters.id = head.currentVersionId")
    UsageParameters current();
}
