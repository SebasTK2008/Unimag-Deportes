package unimagdeportes.service.reservation;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationLock {
    private final EntityManager entityManager;

    public ReservationLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(1157411661)").getSingleResult();
    }
}
