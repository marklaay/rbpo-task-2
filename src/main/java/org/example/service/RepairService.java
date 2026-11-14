package org.example.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.example.api.Commands.*;
import org.example.domain.Models.*;
import org.example.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RepairService {
    private final UserRepository users;
    private final DeviceRepository devices;
    private final OrderRepository orders;
    private final PartRepository parts;
    private final ItemRepository items;
    private final Clock clock;

    public RepairService(UserRepository users, DeviceRepository devices, OrderRepository orders,
                         PartRepository parts, ItemRepository items, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.orders = orders;
        this.parts = parts;
        this.items = items;
        this.clock = clock;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private void checkConflict(boolean condition, String message) {
        if (condition) {
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
    }

    private <T extends Entity> T findRequired(MemoryRepository<T> repo, long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ресурс с id=" + id + " не найден"));
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 500)
            throw badRequest(field + ": требуется непустая строка длиной до 500 символов");
        return value.trim();
    }

    private long requireId(Long value, String field) {
        if (value == null || value <= 0) {
            throw badRequest(field + ": требуется положительный id");
        }
        return value;
    }

    private int requireQuantity(Integer value) {
        if (value == null || value <= 0) {
            throw badRequest("quantity должно быть положительным");
        }
        return value;
    }

    private BigDecimal validateMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("999999999.99")) > 0)
            throw badRequest(field + ": требуется сумма от 0 до 999999999.99, не более двух знаков после точки");
        return value.setScale(2);
    }

    private <T> T valueOrPrevious(T input, T existing) {
        return input == null ? existing : input;
    }

    private boolean isActive(RepairOrder order) {
        return order.status() == OrderStatus.NEW || order.status() == OrderStatus.IN_PROGRESS;
    }

    public synchronized List<User> users() {
        return users.findAll();
    }

    public synchronized User user(long id) {
        return findRequired(users, id);
    }

    public synchronized User createUser(UserInput input) {
        return saveUser(null, input);
    }

    public synchronized User updateUser(long id, UserInput input) {
        return saveUser(user(id), input);
    }

    private User saveUser(User existing, UserInput input) {
        String name = requiredText(valueOrPrevious(input.name(), existing == null ? null : existing.name()), "name");
        String email = requiredText(valueOrPrevious(input.email(), existing == null ? null : existing.email()), "email").toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw badRequest("Некорректный email");
        }
        checkConflict(users.findAll().stream().anyMatch(u -> (existing == null || u.id() != existing.id()) && u.email().equals(email)), "Email уже занят");
        Instant now = clock.instant();
        return users.save(new User(existing == null ? users.nextId() : existing.id(), name, email, existing == null ? now : existing.createdAt(), now));
    }

    public synchronized void deleteUser(long id) {
        user(id);
        checkConflict(devices.findAll().stream().anyMatch(d -> d.ownerId() == id), "У пользователя есть устройства");
        users.delete(id);
    }

    public synchronized List<Device> devices() {
        return devices.findAll();
    }

    public synchronized Device device(long id) {
        return findRequired(devices, id);
    }

    public synchronized Device createDevice(DeviceInput input) {
        return saveDevice(null, input);
    }

    public synchronized Device updateDevice(long id, DeviceInput input) {
        return saveDevice(device(id), input);
    }

    private Device saveDevice(Device existing, DeviceInput input) {
        long ownerId = requireId(valueOrPrevious(input.ownerId(), existing == null ? null : existing.ownerId()), "ownerId");
        user(ownerId);
        String type = requiredText(valueOrPrevious(input.type(), existing == null ? null : existing.type()), "type");
        String model = requiredText(valueOrPrevious(input.model(), existing == null ? null : existing.model()), "model");
        String serial = requiredText(valueOrPrevious(input.serialNumber(), existing == null ? null : existing.serialNumber()), "serialNumber");
        checkConflict(devices.findAll().stream().anyMatch(d -> (existing == null || d.id() != existing.id()) && d.serialNumber().equals(serial)), "Серийный номер уже существует");
        Instant now = clock.instant();
        return devices.save(new Device(existing == null ? devices.nextId() : existing.id(), ownerId, type, model, serial, existing == null ? now : existing.createdAt(), now));
    }

    public synchronized void deleteDevice(long id) {
        device(id);
        checkConflict(orders.findAll().stream().anyMatch(o -> o.deviceId() == id), "У устройства есть ремонтные заказы");
        devices.delete(id);
    }

    public synchronized List<RepairOrder> orders() {
        return orders.findAll();
    }

    public synchronized RepairOrder order(long id) {
        return findRequired(orders, id);
    }

    public synchronized RepairOrder createOrder(OrderInput input) {
        return saveOrder(null, input);
    }

    public synchronized RepairOrder updateOrder(long id, OrderInput input) {
        return saveOrder(order(id), input);
    }

    private RepairOrder saveOrder(RepairOrder existing, OrderInput input) {
        long deviceId = requireId(valueOrPrevious(input.deviceId(), existing == null ? null : existing.deviceId()), "deviceId");
        device(deviceId);
        String malfunction = requiredText(valueOrPrevious(input.malfunction(), existing == null ? null : existing.malfunction()), "malfunction");
        BigDecimal laborCost = validateMoney(valueOrPrevious(input.laborCost(), existing == null ? null : existing.laborCost()), "laborCost");
        OrderStatus status = valueOrPrevious(input.status(), existing == null ? OrderStatus.NEW : existing.status());
        if (existing != null) {
            List<RepairItem> lines = items.findAll().stream().filter(i -> i.orderId() == existing.id()).toList();
            checkConflict(!isActive(existing) && status != existing.status(), "Завершённый или отменённый заказ нельзя открыть повторно");
            checkConflict(deviceId != existing.deviceId() && !lines.isEmpty(), "Нельзя менять устройство заказа с позициями");
            checkConflict((status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED)
                    && lines.stream().anyMatch(i -> i.status() == ItemStatus.RESERVED), "Сначала используйте или отмените резервы");
            checkConflict(status == OrderStatus.CANCELLED && lines.stream().anyMatch(i -> i.status() == ItemStatus.USED),
                    "Нельзя отменить заказ с использованными запчастями");
        }
        Instant now = clock.instant();
        return orders.save(new RepairOrder(existing == null ? orders.nextId() : existing.id(), deviceId, malfunction, status, laborCost, existing == null ? now : existing.createdAt(), now));
    }

    public synchronized void deleteOrder(long id) {
        order(id);
        checkConflict(items.findAll().stream().anyMatch(i -> i.orderId() == id), "В заказе есть позиции ремонта");
        orders.delete(id);
    }

    public synchronized List<SparePart> parts() {
        return parts.findAll();
    }

    public synchronized SparePart part(long id) {
        return findRequired(parts, id);
    }

    public synchronized SparePart createPart(PartInput input) {
        return savePart(null, input);
    }

    public synchronized SparePart updatePart(long id, PartInput input) {
        return savePart(part(id), input);
    }

    private SparePart savePart(SparePart existing, PartInput input) {
        String name = requiredText(valueOrPrevious(input.name(), existing == null ? null : existing.name()), "name");
        BigDecimal price = validateMoney(valueOrPrevious(input.price(), existing == null ? null : existing.price()), "price");
        Integer stock = valueOrPrevious(input.stock(), existing == null ? null : existing.stock());
        if (stock == null || stock < 0) {
            throw badRequest("stock должен быть неотрицательным");
        }
        int reserved = existing == null ? 0 : existing.reserved();
        checkConflict(stock < reserved, "Остаток не может быть меньше резерва");
        Instant now = clock.instant();
        return parts.save(new SparePart(existing == null ? parts.nextId() : existing.id(), name, price, stock, reserved, existing == null ? now : existing.createdAt(), now));
    }

    public synchronized void deletePart(long id) {
        part(id);
        checkConflict(items.findAll().stream().anyMatch(i -> i.sparePartId() == id), "Запчасть используется в позициях ремонта");
        parts.delete(id);
    }

    public synchronized List<RepairItem> items() {
        return items.findAll();
    }

    public synchronized RepairItem item(long id) {
        return findRequired(items, id);
    }

    public synchronized RepairItem createItem(ItemInput input) {
        return saveItem(null, input);
    }

    public synchronized RepairItem updateItem(long id, ItemInput input) {
        return saveItem(item(id), input);
    }

    private RepairItem saveItem(RepairItem existing, ItemInput input) {
        long orderId = requireId(valueOrPrevious(input.orderId(), existing == null ? null : existing.orderId()), "orderId");
        long partId = requireId(valueOrPrevious(input.sparePartId(), existing == null ? null : existing.sparePartId()), "sparePartId");
        int quantity = requireQuantity(valueOrPrevious(input.quantity(), existing == null ? null : existing.quantity()));
        ItemStatus status = valueOrPrevious(input.status(), existing == null ? ItemStatus.RESERVED : existing.status());
        RepairOrder order = order(orderId);
        SparePart part = part(partId);
        if (existing != null) {
            checkConflict(existing.status() == ItemStatus.USED, "Использованную позицию нельзя изменять");
            checkConflict(existing.orderId() != orderId || existing.sparePartId() != partId,
                    "Для замены заказа или запчасти удалите позицию и создайте новую");
        }
        checkConflict(!isActive(order), "Позиции можно изменять только в активном заказе");
        int previousReserve = 0;
        if (existing != null && existing.status() == ItemStatus.RESERVED) {
            previousReserve = existing.quantity();
        }

        long reserved = (long) part.reserved() - previousReserve;
        long stock = part.stock();
        if (status == ItemStatus.RESERVED) {
            reserved += quantity;
        } else if (status == ItemStatus.USED) {
            stock -= quantity;
        }
        checkConflict(stock < 0 || reserved > stock, "Недостаточно свободных запчастей");
        Instant now = clock.instant();
        parts.save(new SparePart(part.id(), part.name(), part.price(), (int) stock, (int) reserved, part.createdAt(), now));
        return items.save(new RepairItem(existing == null ? items.nextId() : existing.id(), orderId, partId, quantity,
                existing == null ? part.price() : existing.fixedPrice(), status, existing == null ? now : existing.createdAt(), now));
    }

    public synchronized void deleteItem(long id) {
        RepairItem item = item(id);
        if (item.status() == ItemStatus.RESERVED) {
            SparePart part = part(item.sparePartId());
            parts.save(new SparePart(part.id(), part.name(), part.price(), part.stock(),
                    part.reserved() - item.quantity(), part.createdAt(), clock.instant()));
        }
        items.delete(id);
    }
}
