package unimagdeportes.repository.space;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import unimagdeportes.model.space.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findForUpdate(UUID id);
}
