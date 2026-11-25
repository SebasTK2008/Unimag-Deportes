package unimagdeportes.repository.block;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import unimagdeportes.model.block.BlockStatus;
import unimagdeportes.model.block.InstitutionalBlock;

public interface InstitutionalBlockRepository extends JpaRepository<InstitutionalBlock, UUID> {
    List<InstitutionalBlock> findBySpaceIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            UUID spaceId, BlockStatus status, Instant end, Instant start);

    @Query("""
            select b from InstitutionalBlock b
            where b.spaceId = :spaceId and b.startAt < :to and b.endAt > :from
              and (:status is null or b.status = :status)
            """)
    Page<InstitutionalBlock> findHistory(UUID spaceId, Instant from, Instant to, BlockStatus status, Pageable pageable);
}
