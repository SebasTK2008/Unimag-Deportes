package unimagdeportes.repository.block;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.block.InstitutionalBlock;

public interface InstitutionalBlockRepository extends JpaRepository<InstitutionalBlock, UUID> {
    List<InstitutionalBlock> findBySpaceIdAndStartAtLessThanAndEndAtGreaterThan(
            UUID spaceId, Instant end, Instant start);
}
