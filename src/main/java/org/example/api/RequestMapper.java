package org.example.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.example.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestMapper {
    private final ObjectMapper mapper;

    public RequestMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(JsonNode body, Class<T> type) {
        if (!body.isObject()) {
            throw badRequest("Ожидается JSON-объект");
        }

        Map<String, Class<?>> allowedFields = new HashMap<>();
        for (RecordComponent component : type.getRecordComponents()) {
            allowedFields.put(component.getName(), component.getType());
        }

        Iterator<Map.Entry<String, JsonNode>> fields = body.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            Class<?> expectedType = allowedFields.get(name);

            if (expectedType == null) {
                throw badRequest("Неизвестное поле: " + name);
            }
            if (value.isNull()) {
                throw badRequest("Поле " + name + " не может быть null");
            }
            if (!hasCorrectType(value, expectedType)) {
                throw badRequest("Неверный тип поля: " + name);
            }
        }

        try {
            return mapper.treeToValue(body, type);
        } catch (JsonProcessingException exception) {
            throw badRequest("Не удалось прочитать значения полей");
        }
    }

    private boolean hasCorrectType(JsonNode value, Class<?> expectedType) {
        if (expectedType == String.class || expectedType.isEnum()) {
            return value.isTextual();
        }
        if (expectedType == Integer.class) {
            return value.isIntegralNumber() && value.canConvertToInt();
        }
        if (expectedType == Long.class) {
            return value.isIntegralNumber() && value.canConvertToLong();
        }
        return value.isNumber();
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
