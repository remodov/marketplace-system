package ru.vikulinva.orderservice.adapter.out.postgres.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ru.vikulinva.ddd.ValueObject;

import java.io.IOException;
import java.lang.reflect.RecordComponent;

/**
 * Правила сериализации внутренних типов во внешнее событие.
 *
 * <p>Value object из одного поля уезжает наружу скаляром: {@code CustomerId}
 * становится строкой, а не объектом {@code {"value": "..."}}. Составной value
 * object пишется по своим компонентам — и только по ним, поэтому производные
 * геттеры вроде {@code Money.isZero()} в контракт не протекают.
 *
 * <p>Причина конкретная: раньше {@code customerId} уезжал в Kafka вложенным
 * объектом, консьюмер читал его строкой и падал — адресат уведомления не
 * определялся вовсе.
 */
public class ExternalContractModule extends SimpleModule {

    public ExternalContractModule() {
        addSerializer(ValueObject.class, new ValueObjectSerializer());
    }

    private static final class ValueObjectSerializer extends JsonSerializer<ValueObject> {

        @Override
        public void serialize(ValueObject value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
            RecordComponent[] components = value.getClass().getRecordComponents();
            if (components == null) {
                gen.writeString(String.valueOf(value));
                return;
            }
            if (components.length == 1) {
                gen.writeObject(read(value, components[0]));
                return;
            }
            gen.writeStartObject();
            for (RecordComponent component : components) {
                gen.writeFieldName(component.getName());
                gen.writeObject(read(value, component));
            }
            gen.writeEndObject();
        }

        private Object read(ValueObject value, RecordComponent component) {
            try {
                return component.getAccessor().invoke(value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                    "Не прочитать компонент " + component.getName() + " у " + value.getClass().getSimpleName(), e);
            }
        }
    }
}
