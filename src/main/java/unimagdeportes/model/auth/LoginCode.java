package unimagdeportes.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_code")
public class LoginCode {
    @Id
    private UUID id;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 128)
    private String codeHash;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;
    private Instant usedAt;
    @Column(nullable = false)
    private Instant createdAt;

    protected LoginCode() { }

    public LoginCode(UUID id, String email, String codeHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void recordFailedAttempt() { attempts++; }
    public void markUsed(Instant now) { usedAt = now; }
}
