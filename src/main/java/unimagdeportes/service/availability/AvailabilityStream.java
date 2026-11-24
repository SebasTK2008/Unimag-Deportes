package unimagdeportes.service.availability;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import unimagdeportes.dto.availability.AvailabilityDtos.AvailabilityChanged;
import unimagdeportes.event.AvailabilityChangedEvent;

@Service
public class AvailabilityStream {
    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityStream.class);
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Clock clock;

    public AvailabilityStream(Clock clock) {
        this.clock = clock;
    }

    public SseEmitter subscribe(UUID spaceId) {
        var emitter = new SseEmitter(0L);
        var subscription = new Subscription(spaceId, emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException error) {
            subscriptions.remove(subscription);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    @TransactionalEventListener
    public void onAvailabilityChanged(AvailabilityChangedEvent event) {
        var payload = new AvailabilityChanged(event.spaceId(), clock.instant().atZone(ZONE).toOffsetDateTime());
        subscriptions.stream().filter(subscription -> subscription.spaceId().equals(event.spaceId()))
                .forEach(subscription -> send(subscription, payload));
    }

    @EventListener(ContextClosedEvent.class)
    public void closeSubscriptions() {
        subscriptions.forEach(subscription -> subscription.emitter().complete());
        subscriptions.clear();
    }

    private void send(Subscription subscription, AvailabilityChanged payload) {
        try {
            subscription.emitter().send(SseEmitter.event().name("availability-changed").data(payload));
        } catch (IOException | IllegalStateException error) {
            subscriptions.remove(subscription);
            subscription.emitter().completeWithError(error);
            LOG.debug("Suscripción SSE cerrada para el espacio {}", subscription.spaceId());
        }
    }

    private record Subscription(UUID spaceId, SseEmitter emitter) { }
}
