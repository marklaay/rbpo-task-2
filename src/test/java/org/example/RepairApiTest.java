package org.example;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.example.api.Commands.*;
import org.example.domain.Models.*;
import org.example.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RepairApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RepairService service;
    static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @BeforeEach void reset() {
        service.items().forEach(i -> service.deleteItem(i.id()));
        service.orders().forEach(o -> service.deleteOrder(o.id()));
        service.devices().forEach(d -> service.deleteDevice(d.id()));
        service.parts().forEach(p -> service.deletePart(p.id()));
        service.users().forEach(u -> service.deleteUser(u.id()));
    }
    JsonNode postResource(String resource, Object body) throws Exception {
        String response = mvc.perform(post("/api/" + resource).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
    record Fixture(long user, long device, long order, long part) {}
    Fixture fixture(int stock) throws Exception {
        long user = postResource("users", Map.of("name","Иван", "email","ivan@example.com")).get("id").asLong();
        long device = postResource("devices", Map.of("ownerId",user,"type","Стиральная машина","model","M1","serialNumber","SN-1")).get("id").asLong();
        long order = postResource("repair-orders", Map.of("deviceId",device,"malfunction","Не сливает воду","laborCost",1500)).get("id").asLong();
        long part = postResource("spare-parts", Map.of("name","Насос","price",100,"stock",stock)).get("id").asLong();
        return new Fixture(user,device,order,part);
    }
    @ParameterizedTest
    @ValueSource(strings={"users","devices","repair-orders","spare-parts","repair-items"})
    void fiveCrudOperations(String resource) throws Exception {
        Fixture f = fixture(10);
        Object body = switch(resource) {
            case "users" -> Map.of("name","Пётр","email","petr@example.com");
            case "devices" -> Map.of("ownerId",f.user,"type","Чайник","model","K1","serialNumber","SN-2");
            case "repair-orders" -> Map.of("deviceId",f.device,"malfunction","Не включается","laborCost",500);
            case "spare-parts" -> Map.of("name","Плата","price",200,"stock",5);
            default -> Map.of("orderId",f.order,"sparePartId",f.part,"quantity",2);
        };
        JsonNode created = postResource(resource,body);
        long id = created.get("id").asLong();
        assertEquals(NOW.toString(), created.get("createdAt").asText());
        mvc.perform(get("/api/" + resource)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/" + resource + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        String patch = switch(resource) {
            case "users", "spare-parts" -> "{\"name\":\"Новое имя\"}";
            case "devices" -> "{\"model\":\"Новая модель\"}";
            case "repair-orders" -> "{\"laborCost\":750.25}";
            default -> "{\"quantity\":3}";
        };
        mvc.perform(patch("/api/" + resource + "/" + id).contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete("/api/" + resource + "/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/" + resource + "/" + id)).andExpect(status().isNotFound());
    }
    @Test void reserveUseAndKeepPrice() throws Exception {
        Fixture f = fixture(5);
        long line = postResource("repair-items", Map.of("orderId",f.order,"sparePartId",f.part,"quantity",2)).get("id").asLong();
        assertEquals(2, service.part(f.part).reserved());
        assertEquals(5, service.part(f.part).stock());
        service.updatePart(f.part,new PartInput(null,new BigDecimal("900"),null));
        mvc.perform(patch("/api/repair-items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"USED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fixedPrice").value(100));
        assertEquals(0,service.part(f.part).reserved());
        assertEquals(3,service.part(f.part).stock());
        mvc.perform(patch("/api/repair-items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        service.deleteItem(line);
        assertEquals(3,service.part(f.part).stock());
    }
    @Test void shortageIsAtomicAndCancellationReleasesReserve() throws Exception {
        Fixture f = fixture(3);
        long line = postResource("repair-items",Map.of("orderId",f.order,"sparePartId",f.part,"quantity",2)).get("id").asLong();
        mvc.perform(patch("/api/repair-items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/repair-items").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("orderId",f.order,"sparePartId",f.part,"quantity",2))))
                .andExpect(status().isConflict());
        assertEquals(2,service.item(line).quantity());
        assertEquals(2,service.part(f.part).reserved());
        service.updateItem(line,new ItemInput(null,null,null,ItemStatus.CANCELLED));
        assertEquals(0,service.part(f.part).reserved());
        service.updateItem(line,new ItemInput(null,null,3,ItemStatus.RESERVED));
        service.deleteItem(line);
        assertEquals(0,service.part(f.part).reserved());
        assertEquals(3,service.part(f.part).stock());
    }
    @Test void allForeignKeysAreProtected() throws Exception {
        Fixture f = fixture(3);
        postResource("repair-items", Map.of("orderId",f.order,"sparePartId",f.part,"quantity",1));
        for (String path : List.of("users/"+f.user,"devices/"+f.device,"repair-orders/"+f.order,"spare-parts/"+f.part))
            mvc.perform(delete("/api/" + path)).andExpect(status().isConflict());
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":999999,\"type\":\"t\",\"model\":\"m\",\"serialNumber\":\"s\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void rejectClientTimeAndReadOnlyFields() throws Exception {
        for (String field : List.of("createdAt","updatedAt","id"))
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"A\",\"email\":\"a@b.ru\",\"" + field + "\":\"2020-01-01T00:00:00Z\"}"))
                    .andExpect(status().isBadRequest());
        Fixture f = fixture(3);
        mvc.perform(patch("/api/spare-parts/" + f.part).contentType(MediaType.APPLICATION_JSON).content("{\"reserved\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/repair-items").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("orderId",f.order,"sparePartId",f.part,"quantity",1,"fixedPrice",1))))
                .andExpect(status().isBadRequest());
    }
    @ParameterizedTest
    @ValueSource(strings={"{", "[]", "null", "{}", "{\"name\":null}", "{\"name\":123,\"email\":\"a@b.ru\"}",
            "{\"name\":\" \",\"email\":\"a@b.ru\"}", "{\"name\":\"A\",\"email\":\"bad\"}"})
    void rejectInvalidUsers(String body) throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void validateStockMoneyAndPartialUpdate() throws Exception {
        Fixture f = fixture(3);
        for (String body : List.of("{\"stock\":-1}","{\"stock\":1.5}","{\"stock\":\"3\"}",
                "{\"price\":-1}","{\"price\":1.234}","{\"price\":null}"))
            mvc.perform(patch("/api/spare-parts/"+f.part).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/"+f.user).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Павел\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ivan@example.com"));
        postResource("repair-items", Map.of("orderId",f.order,"sparePartId",f.part,"quantity",2));
        mvc.perform(patch("/api/spare-parts/"+f.part).contentType(MediaType.APPLICATION_JSON).content("{\"stock\":1}"))
                .andExpect(status().isConflict());
    }
    @Test void terminalOrdersAndEnums() throws Exception {
        Fixture f = fixture(3);
        long line = postResource("repair-items", Map.of("orderId",f.order,"sparePartId",f.part,"quantity",1)).get("id").asLong();
        mvc.perform(patch("/api/repair-orders/"+f.order).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());
        for (String body : List.of("{\"status\":\"UNKNOWN\"}","{\"status\":1}"))
            mvc.perform(patch("/api/repair-orders/"+f.order).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        service.updateItem(line,new ItemInput(null,null,null,ItemStatus.USED));
        service.updateOrder(f.order,new OrderInput(null,null,OrderStatus.COMPLETED,null));
        mvc.perform(post("/api/repair-items").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("orderId",f.order,"sparePartId",f.part,"quantity",1))))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/repair-orders/"+f.order).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NEW\"}"))
                .andExpect(status().isConflict());
    }
    @Test void concurrentReservationsCannotOversell() throws Exception {
        Fixture f = fixture(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            Callable<Boolean> reserve = () -> {
                gate.await();
                try { service.createItem(new ItemInput(f.order,f.part,1,ItemStatus.RESERVED)); return true; }
                catch (ApiException ex) { assertEquals(409,ex.status().value()); return false; }
            };
            Future<Boolean> first = executor.submit(reserve);
            Future<Boolean> second = executor.submit(reserve);
            gate.countDown();
            assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
        }
        assertEquals(1,service.part(f.part).reserved());
        assertEquals(1,service.items().size());
    }
}
