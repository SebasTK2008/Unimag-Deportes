package unimagdeportes.repository.parameter;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import unimagdeportes.model.parameter.UsageParameters;

public interface UsageParametersRepository extends JpaRepository<UsageParameters, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select parameters from UsageParameters parameters where parameters.id = 1")
    UsageParameters lockCurrent();
}
