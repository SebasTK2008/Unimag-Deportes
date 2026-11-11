package unimagdeportes.repository.space;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.space.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> { }
