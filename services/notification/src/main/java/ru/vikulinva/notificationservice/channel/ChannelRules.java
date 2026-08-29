package ru.vikulinva.notificationservice.channel;

import org.springframework.stereotype.Component;
import ru.vikulinva.notificationservice.generated.enums.NotificationChannel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BR-N2: фиксированная таблица «event-type → набор каналов». На Tier A
 * это hard-coded mapping в коде; Tier B вынесет в БД.
 */
@Component
public class ChannelRules {

    private static final Map<String, Set<NotificationChannel>> CHANNELS = Map.ofEntries(
        Map.entry("OrderConfirmed",  Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)),
        Map.entry("OrderPaid",       Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)),
        Map.entry("OrderShipped",    Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)),
        Map.entry("OrderDelivered",  Set.of(NotificationChannel.EMAIL)),
        Map.entry("OrderCancelled",  Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)),
        Map.entry("OrderRefunded",   Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH)),
        Map.entry("DisputeOpened",   Set.of(NotificationChannel.PUSH)),
        Map.entry("DisputeResolved", Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH))
    );

    public List<NotificationChannel> channelsFor(String eventType) {
        Set<NotificationChannel> set = CHANNELS.get(eventType);
        return set == null ? List.of() : set.stream().sorted().toList();
    }

    /**
     * Ключ шаблона для пары event-type + channel.
     * Пример: {@code OrderConfirmed} + {@code EMAIL} → {@code "order.confirmed.email"}.
     */
    public String templateKey(String eventType, NotificationChannel channel) {
        String base = switch (eventType) {
            case "OrderConfirmed"  -> "order.confirmed";
            case "OrderPaid"       -> "order.paid";
            case "OrderShipped"    -> "order.shipped";
            case "OrderDelivered"  -> "order.delivered";
            case "OrderCancelled"  -> "order.cancelled";
            case "OrderRefunded"   -> "order.refunded";
            case "DisputeOpened"   -> "dispute.opened";
            case "DisputeResolved" -> "dispute.resolved";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
        return base + "." + channel.name().toLowerCase();
    }
}
