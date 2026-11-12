package unimagdeportes.repository.parameter;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import unimagdeportes.model.parameter.UsageParametersHead;

public interface UsageParametersHeadRepository extends JpaRepository<UsageParametersHead, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select head from UsageParametersHead head where head.id = 1")
    UsageParametersHead lockCurrent();
}
