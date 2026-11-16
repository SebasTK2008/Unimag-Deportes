package unimagdeportes.mapper.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.Space;
import unimagdeportes.model.space.SpaceImage;
import unimagdeportes.model.space.SpaceStatus;

class SpaceMapperTest {
    private final SpaceMapper mapper = Mappers.getMapper(SpaceMapper.class);

    @Test
    void mapsSpaceFieldsWithoutExposingImages() {
        UUID id = UUID.randomUUID();
        var image = new SpaceImage("image/png", new byte[]{1});
        var space = new Space(id, "Cancha", 12, "Calzado", LocalTime.of(6, 0), LocalTime.of(22, 0),
                SpaceStatus.ACTIVE, Set.of(Discipline.VOLLEYBALL, Discipline.BASKETBALL), image, image);

        var result = mapper.toInfo(space);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Cancha");
        assertThat(result.capacity()).isEqualTo(12);
        assertThat(result.requirements()).isEqualTo("Calzado");
        assertThat(result.openingTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(result.closingTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(result.status()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(result.disciplines()).containsExactlyInAnyOrder(Discipline.VOLLEYBALL, Discipline.BASKETBALL);
        assertThatThrownBy(() -> result.disciplines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsImageContentWithoutExposingStoredArray() {
        var image = new SpaceImage("image/png", new byte[]{1, 2});

        var result = mapper.toImageContent(image);
        result.content()[0] = 9;

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getContent()).containsExactly((byte) 1, (byte) 2);
    }
}
