package event_service.event_service.pricetier;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "event_price_tier")
public class EventPriceTier {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    protected EventPriceTier() {
    }

    public EventPriceTier(UUID eventId, BigDecimal price, String currency) {
        this.eventId = eventId;
        this.price = price;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}
