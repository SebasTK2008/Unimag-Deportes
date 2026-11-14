package unimagdeportes.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import unimagdeportes.event.ReservationCreatedEvent;
import unimagdeportes.mail.MailSender;
import unimagdeportes.mail.ReceiptPdfGenerator;

@Service
public class ReceiptService {
    private static final Logger LOG = LoggerFactory.getLogger(ReceiptService.class);
    private final MailSender mail;
    private final ReceiptPdfGenerator pdf;

    public ReceiptService(MailSender mail, ReceiptPdfGenerator pdf) {
        this.mail = mail;
        this.pdf = pdf;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        try {
            var receipt = event.receipt();
            if (receipt.contactUrl() == null || receipt.contactUrl().isBlank()) {
                throw new IllegalStateException("Canal de contacto no configurado");
            }
            mail.sendWithPdf(receipt.recipient(), "Comprobante de reserva",
                    "Tu reserva ha sido confirmada.\n\nContacta al administrador: " + receipt.contactUrl(),
                    "comprobante.pdf", pdf.generate(receipt));
        } catch (RuntimeException ex) {
            LOG.error("No fue posible enviar el comprobante: {}", ex.getClass().getSimpleName());
        }
    }
}
