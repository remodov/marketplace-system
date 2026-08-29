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
        // TODO шаг 10: научить сериализацию правилам внешнего контракта.
        // Value object из одного поля — скаляр; составной — свои компоненты,
        // и только они.
    }
}
