package unimagdeportes.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import unimagdeportes.exception.BusinessException;

class StorageObjectCleanupTest {
    private static final String DUE = "select storage_key from storage_object_cleanup where not_before <= ? "
            + "order by not_before, storage_key limit 1 for update skip locked";
    private static final String REFERENCED = "select exists(select 1 from space_image where storage_key = ?)";
    private static final String DELETE = "delete from storage_object_cleanup where storage_key = ?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");
    private final StorageObjectCleanup cleanup = new StorageObjectCleanup(jdbc, storage,
            Clock.fixed(now, ZoneOffset.UTC), mock(PlatformTransactionManager.class));

    @Test
    void tracksUploadBeforeItCanBeRetainedAndRejectsExpiredUpload() {
        cleanup.trackUpload("new");
        verify(jdbc).update("insert into storage_object_cleanup (storage_key, not_before) values (?, ?)",
                "new", Timestamp.from(now.plusSeconds(3600)));
        assertThatThrownBy(() -> cleanup.retain("new")).isInstanceOf(IllegalStateException.class);
        when(jdbc.update(DELETE, "new")).thenReturn(1);
        cleanup.retain("new");
        cleanup.finishUpload("new");
        verify(jdbc).update("insert into storage_object_cleanup (storage_key, not_before) values (?, ?) "
                        + "on conflict (storage_key) do update set not_before = excluded.not_before",
                "new", Timestamp.from(now.plusSeconds(3600)));
    }

    @Test
    void removesUnreferencedObjectsButProtectsLiveReferences() {
        when(jdbc.queryForList(eq(DUE), eq(String.class), any(Timestamp.class)))
                .thenReturn(List.of("orphan")).thenReturn(List.of("live")).thenReturn(List.of());
        when(jdbc.queryForObject(REFERENCED, Boolean.class, "orphan")).thenReturn(false);
        when(jdbc.queryForObject(REFERENCED, Boolean.class, "live")).thenReturn(true);
        cleanup.clean();
        verify(storage).delete("orphan");
        verify(storage, never()).delete("live");
        verify(jdbc).update(DELETE, "orphan");
        verify(jdbc).update(DELETE, "live");
    }

    @Test
    void failedDeletionIsRetriedLaterWithoutDiscardingTheQueueEntry() {
        when(jdbc.queryForList(eq(DUE), eq(String.class), any(Timestamp.class)))
                .thenReturn(List.of("orphan")).thenReturn(List.of());
        when(jdbc.queryForObject(REFERENCED, Boolean.class, "orphan")).thenReturn(false);
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Unavailable"))
                .when(storage).delete("orphan");
        cleanup.clean();
        verify(jdbc).update("update storage_object_cleanup set not_before = ? where storage_key = ?",
                Timestamp.from(now.plusSeconds(300)), "orphan");
        verify(jdbc, never()).update(DELETE, "orphan");
    }
}
