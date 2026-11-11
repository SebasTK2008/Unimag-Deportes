package unimagdeportes.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    private String name;
    private Instant suspendedUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() { }

    public AppUser(UUID id, String email, Role role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Instant getSuspendedUntil() { return suspendedUntil; }

    public void completeProfile(String name) {
        this.name = name;
    }
}
