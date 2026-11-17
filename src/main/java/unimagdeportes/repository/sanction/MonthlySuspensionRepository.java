package unimagdeportes.repository.sanction;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Marca única por usuario y mes de inasistencia (plan general §4.1). Garantiza como máximo
 * una suspensión por mes incluso ante reintentos concurrentes del job (RN-38).
 */
@Repository
public class MonthlySuspensionRepository {
    private final JdbcTemplate jdbc;

    public MonthlySuspensionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve {@code true} solo si la marca no existía; {@code absenceMonth} es el primer día del mes. */
    public boolean register(UUID userId, LocalDate absenceMonth, Instant createdAt, Instant suspendedUntil) {
        return jdbc.update("""
                insert into monthly_suspension (user_id, absence_month, created_at, suspended_until) values (?, ?, ?, ?)
                on conflict (user_id, absence_month) do nothing
                """, userId, absenceMonth, Timestamp.from(createdAt), Timestamp.from(suspendedUntil)) == 1;
    }

}
