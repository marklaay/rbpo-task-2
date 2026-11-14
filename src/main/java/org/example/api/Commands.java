package org.example.api;

import java.math.BigDecimal;
import org.example.domain.Models.*;

public final class Commands {
    private Commands() {}

    public record UserInput(
            String name,
            String email
    ) {}

    public record DeviceInput(
            Long ownerId,
            String type,
            String model,
            String serialNumber
    ) {}

    public record OrderInput(
            Long deviceId,
            String malfunction,
            OrderStatus status,
            BigDecimal laborCost
    ) {}

    public record PartInput(
            String name,
            BigDecimal price,
            Integer stock
    ) {}

    public record ItemInput(
            Long orderId,
            Long sparePartId,
            Integer quantity,
            ItemStatus status
    ) {}
}
