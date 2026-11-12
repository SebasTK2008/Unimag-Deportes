package unimagdeportes.event;

import unimagdeportes.dto.mail.Receipt;

public record ReservationCreatedEvent(Receipt receipt) { }
