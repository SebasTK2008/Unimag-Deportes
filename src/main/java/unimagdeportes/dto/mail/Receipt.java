package unimagdeportes.dto.mail;

import java.time.Instant;
import java.util.List;

public record Receipt(String recipient, String userName, String spaceName, Instant startAt, Instant endAt,
        List<EquipmentItem> equipment, String cancellationRules, String contactUrl) {
    public Receipt {
        equipment = List.copyOf(equipment);
    }

    public record EquipmentItem(String name, int quantity) { }
}
