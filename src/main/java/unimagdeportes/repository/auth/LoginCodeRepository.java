package unimagdeportes.repository.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.auth.LoginCode;

public interface LoginCodeRepository extends JpaRepository<LoginCode, UUID> {
    Optional<LoginCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    List<LoginCode> findByEmailAndCreatedAtAfterOrderByCreatedAtAsc(String email, Instant since);
}
