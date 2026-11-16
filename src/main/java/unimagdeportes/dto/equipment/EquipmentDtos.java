package unimagdeportes.dto.equipment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;
import unimagdeportes.dto.StrictIntegerDeserializer;
import unimagdeportes.model.equipment.EquipmentStatus;
import unimagdeportes.model.space.Discipline;

public final class EquipmentDtos {
    private EquipmentDtos() { }

    public record Input(String name, Discipline discipline,
            @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer stock, EquipmentStatus status) { }
    public record Info(UUID id, String name, Discipline discipline, int stock, EquipmentStatus status) { }
    public record Catalog(List<Info> equipment) { }
    public record AvailabilityItem(UUID equipmentId, String name, int stock, int committed, int available) { }
    public record Availability(OffsetDateTime startAt, OffsetDateTime endAt, List<AvailabilityItem> items) { }

    public static class Update {
        private String name;
        private Discipline discipline;
        private Integer stock;
        private EquipmentStatus status;
        private boolean namePresent;
        private boolean disciplinePresent;
        private boolean stockPresent;
        private boolean statusPresent;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; this.namePresent = true; }
        public boolean isNamePresent() { return namePresent; }
        public Discipline getDiscipline() { return discipline; }
        public void setDiscipline(Discipline discipline) {
            this.discipline = discipline;
            this.disciplinePresent = true;
        }
        public boolean isDisciplinePresent() { return disciplinePresent; }
        public Integer getStock() { return stock; }
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        public void setStock(Integer stock) { this.stock = stock; this.stockPresent = true; }
        public boolean isStockPresent() { return stockPresent; }
        public EquipmentStatus getStatus() { return status; }
        public void setStatus(EquipmentStatus status) { this.status = status; this.statusPresent = true; }
        public boolean isStatusPresent() { return statusPresent; }
    }
}
