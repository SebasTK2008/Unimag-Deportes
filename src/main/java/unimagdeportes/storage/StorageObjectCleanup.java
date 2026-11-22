package unimagdeportes.storage;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;

/** Durable compensation for objects whose upload or replacement outlives a DB transaction. */
@Service
public class StorageObjectCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(StorageObjectCleanup.class);
    static final Duration UPLOAD_GRACE = Duration.ofHours(1);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final ImageStorage storage;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public StorageObjectCleanup(JdbcTemplate jdbc, ImageStorage storage, Clock clock, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void trackUpload(String key) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "insert into storage_object_cleanup (storage_key, not_before) values (?, ?)",
                key, Timestamp.from(clock.instant().plus(UPLOAD_GRACE))));
    }

    /** Recreates tracking if cleanup ran while an unusually delayed upload was still resolving. */
    public void finishUpload(String key) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "insert into storage_object_cleanup (storage_key, not_before) values (?, ?) "
                        + "on conflict (storage_key) do update set not_before = excluded.not_before",
                key, Timestamp.from(clock.instant().plus(UPLOAD_GRACE))));
    }

    /** Called inside the transaction that installs the reference; locks against the cleanup worker. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retain(String key) {
        if (jdbc.update("delete from storage_object_cleanup where storage_key = ?", key) != 1) {
            throw new IllegalStateException("La subida expiró antes de confirmar su referencia.");
        }
    }

    /** Called inside the replacement transaction, so rollback also cancels deletion of the old file. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleDeletion(String key) {
        jdbc.update("insert into storage_object_cleanup (storage_key, not_before) values (?, ?) "
                + "on conflict (storage_key) do nothing", key, Timestamp.from(clock.instant()));
    }

    public void clean() {
        for (int count = 0; count < 20; count++) {
            Boolean processed = transactions.execute(status -> {
                var keys = jdbc.queryForList("select storage_key from storage_object_cleanup where not_before <= ? "
                        + "order by not_before, storage_key limit 1 for update skip locked",
                        String.class, Timestamp.from(clock.instant()));
                if (keys.isEmpty()) return false;
                String key = keys.getFirst();
                Boolean referenced = jdbc.queryForObject(
                        "select exists(select 1 from space_image where storage_key = ?)", Boolean.class, key);
                if (!Boolean.TRUE.equals(referenced)) {
                    try {
                        storage.delete(key);
                    } catch (BusinessException exception) {
                        jdbc.update("update storage_object_cleanup set not_before = ? where storage_key = ?",
                                Timestamp.from(clock.instant().plus(RETRY_DELAY)), key);
                        LOG.warn("Limpieza de imagen aplazada: {}", exception.getCode());
                        return true;
                    }
                }
                jdbc.update("delete from storage_object_cleanup where storage_key = ?", key);
                return true;
            });
            if (!Boolean.TRUE.equals(processed)) return;
        }
    }
}
