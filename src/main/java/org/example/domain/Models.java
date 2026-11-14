package org.example.domain;

import java.math.BigDecimal;
import java.time.Instant;

public final class Models {
    private Models() {}
    public interface Entity {
        long id();
    }

    public enum OrderStatus {
        NEW,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }


    public enum ItemStatus {
        RESERVED,
        USED,
        CANCELLED
    }


    public record User(
            long id,
            String name,
            String email,
            Instant createdAt,
            Instant updatedAt
    ) implements Entity {}

    public record Device(
            long id,
            long ownerId,
            String type,
            String model,
            String serialNumber,
            Instant createdAt,
            Instant updatedAt
    ) implements Entity {}

    public record RepairOrder(
            long id,
            long deviceId,
            String malfunction,
            OrderStatus status,
            BigDecimal laborCost,
            Instant createdAt,
            Instant updatedAt
    ) implements Entity {}

    public record SparePart(
            long id,
            String name,
            BigDecimal price,
            int stock,
            int reserved,
            Instant createdAt,
            Instant updatedAt
    ) implements Entity {}

    public record RepairItem(
            long id,
            long orderId,
            long sparePartId,
            int quantity,
            BigDecimal fixedPrice,
            ItemStatus status,
            Instant createdAt,
            Instant updatedAt
    ) implements Entity {}
}
