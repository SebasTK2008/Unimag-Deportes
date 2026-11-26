package unimagdeportes.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.model.user.Role;
import unimagdeportes.repository.user.AppUserRepository;

class UserDisplayNamesTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final UserService service = new UserService(users, Clock.systemUTC(), "");

    @Test
    void returnsOnlyAvailableCurrentNamesWithoutFilteringRole() {
        var formerAdmin = user("  Ana Martínez  ");
        var blank = user("  ");
        var unnamed = user(null);
        UUID missing = UUID.randomUUID();
        var ids = List.of(formerAdmin.getId(), blank.getId(), unnamed.getId(), missing);
        when(users.findAllById(ids)).thenReturn(List.of(formerAdmin, blank, unnamed));
        assertThat(service.displayNames(ids)).containsOnlyKeys(formerAdmin.getId())
                .containsEntry(formerAdmin.getId(), "Ana Martínez");
        verify(users).findAllById(ids);
    }

    @Test
    void emptyActorsDoNotQueryRepository() {
        assertThat(service.displayNames(List.of())).isEmpty();
        verifyNoInteractions(users);
    }

    private AppUser user(String name) {
        var user = new AppUser(UUID.randomUUID(), UUID.randomUUID() + "@unimagdalena.edu.co", Role.USER, Instant.EPOCH);
        user.completeProfile(name);
        return user;
    }
}
