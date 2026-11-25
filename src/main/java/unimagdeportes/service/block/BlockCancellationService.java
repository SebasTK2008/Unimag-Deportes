package unimagdeportes.service.block;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.block.BlockDtos.AdminBlock;
import unimagdeportes.dto.block.BlockDtos.CancelBlock;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockStatus;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;

/** Cancela una fila sin modificar reservas ni propagar el cambio a su recurrencia. */
@Service
public class BlockCancellationService {
    private static final int MAX_REASON_LENGTH = 1000;
    private final InstitutionalBlockRepository blocks;
    private final ReservationLock lock;
    private final Clock clock;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final BlockMapper mapper;

    public BlockCancellationService(InstitutionalBlockRepository blocks, ReservationLock lock, Clock clock,
            AuditService audit, ApplicationEventPublisher events, BlockMapper mapper) {
        this.blocks = blocks;
        this.lock = lock;
        this.clock = clock;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public AdminBlock cancel(UUID id, CancelBlock request, UUID administratorId) {
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > MAX_REASON_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "El motivo de cancelación es obligatorio y admite hasta 1000 caracteres.",
                    Map.of("fields", Map.of("reason", "debe contener entre 1 y 1000 caracteres")));
        }
        lock.acquire();
        var block = blocks.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "El bloqueo no existe."));
        if (block.getStatus() == BlockStatus.CANCELLED) return mapper.toAdminBlock(block);
        var now = clock.instant();
        if (!now.isBefore(block.getEndAt())) {
            throw new BusinessException(HttpStatus.CONFLICT, "BLOCK_NOT_CANCELLABLE",
                    "El bloqueo ya terminó y no se puede cancelar.");
        }
        // PostgreSQL persiste microsegundos; la primera respuesta y sus reintentos deben coincidir.
        block.cancel(now.truncatedTo(ChronoUnit.MICROS), administratorId, reason);
        audit.record("BLOCK_CANCELLED", "INSTITUTIONAL_BLOCK", id, administratorId, reason);
        events.publishEvent(new AvailabilityChangedEvent(block.getSpaceId()));
        return mapper.toAdminBlock(block);
    }
}
