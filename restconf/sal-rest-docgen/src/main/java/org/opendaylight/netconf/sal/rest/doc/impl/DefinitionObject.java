/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

public class DefinitionObject {
    private final HashMap<String, DataType<?>> data;
    private final DefinitionObject parent;

    public DefinitionObject() {
        this.parent = null;
        this.data = new HashMap<>(500, 0.8f);
    }

    public DefinitionObject(final DefinitionObject parent) {
        this.parent = parent.getParent().orElse(parent);
        this.data = new HashMap<>(8, 0.8f);
    }

    private DefinitionObject(final DefinitionObject parent, final HashMap<String, DataType<?>> data) {
        this.data = data;
        this.parent = parent.getParent().orElse(parent);
    }

    public DefinitionObject createCopy(final DefinitionObject newParent) {
        return new DefinitionObject(newParent, new HashMap<>(this.data));
    }

    public HashMap<String, DataType<?>> getData() {
        return data;
    }


    public Optional<DefinitionObject> getParent() {
        return Optional.ofNullable(parent);
    }

    public void addData(final String key, final String value) {
        data.put(key, new DataType<>(value, String.class));
    }

    public void addData(final String key, final Integer value) {
        data.put(key, new DataType<>(value, Integer.class));
    }

    public void addData(final String key, final Long value) {
        data.put(key, new DataType<>(value, Long.class));
    }

    public void addData(final String key, final Double value) {
        data.put(key, new DataType<>(value, Double.class));
    }

    public void addData(final String key, final Float value) {
        data.put(key, new DataType<>(value, Float.class));
    }

    public void addData(final String key, final Short value) {
        data.put(key, new DataType<>(value, Short.class));
    }

    public void addData(final String key, final BigDecimal value) {
        data.put(key, new DataType<>(value, BigDecimal.class));
    }

    public void addData(final String key, final Boolean value) {
        data.put(key, new DataType<>(value, Boolean.class));
    }

    public void addData(final String key, final String[] value) {
        data.put(key, new DataType<>(value, String[].class));
    }

    public void addData(final String key, final Integer[] value) {
        data.put(key, new DataType<>(value, Integer[].class));
    }

    public void addData(final String key, final Long[] value) {
        data.put(key, new DataType<>(value, Long[].class));
    }

    public void addData(final String key, final BigDecimal[] value) {
        data.put(key, new DataType<>(value, BigDecimal[].class));
    }

    public void addData(final String key, final Boolean[] value) {
        data.put(key, new DataType<>(value, Boolean[].class));
    }

    public void addData(final String key, final DefinitionObject value) {
        data.put(key, new DataType<>(value, DefinitionObject.class));
    }

    public void addData(final String key, final DataType<?> value) {
        data.put(key, value);
    }


    public DefinitionObject getRoot() {
        if (parent == null) {
            return this;
        } else {
            return parent;
        }
    }

    public ObjectNode convertToObjectNode() {
        final ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (final Entry<String, DataType<?>> entry : data.entrySet()) {
            DataType dataType = entry.getValue();
            switch (dataType.getClazz()) {
                case INTEGER -> {
                    result.put(entry.getKey(), (Integer) dataType.getValue());
                }
                case STRING -> {
                    result.put(entry.getKey(), (String) dataType.getValue());
                }
                case BOOLEAN -> {
                    result.put(entry.getKey(), (Boolean) dataType.getValue());
                }
                case BIG_DECIMAL -> {
                    result.put(entry.getKey(), (BigDecimal) dataType.getValue());
                }
                case LONG -> {
                    result.put(entry.getKey(), (Long) dataType.getValue());
                }
                case DOUBLE -> {
                    result.put(entry.getKey(), (Double) dataType.getValue());
                }
                case FLOAT -> {
                    result.put(entry.getKey(), (Float) dataType.getValue());
                }
                case SHORT -> {
                    result.put(entry.getKey(), (Short) dataType.getValue());
                }
                case DEFINITION_OBJECT -> {
                    DefinitionObject value = (DefinitionObject) dataType.getValue();
                    result.set(entry.getKey(), value.convertToObjectNode());
                }
                case STRING_ARRAY -> {
                    ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    String[] value = (String[]) dataType.getValue();
                    for (String val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case INTEGER_ARRAY -> {
                    ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    Integer[] value = (Integer[]) dataType.getValue();
                    for (Integer val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case BOOLEAN_ARRAY -> {
                    ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    Boolean[] value = (Boolean[]) dataType.getValue();
                    for (Boolean val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case BIG_DECIMAL_ARRAY -> {
                    ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    BigDecimal[] value = (BigDecimal[]) dataType.getValue();
                    for (BigDecimal val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case LONG_ARRAY -> {
                    ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    Long[] value = (Long[]) dataType.getValue();
                    for (Long val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                default -> {
                }
            }
        }
        return result;
    }

    public static final class DataType<T> {
        private final T value;
        private final ObjectType clazz;

        private DataType(T value, Class<T> clazz) {
            this.value = value;
            this.clazz = ObjectType.fromClass(clazz);
        }

        public T getValue() {
            return value;
        }

        public ObjectType getClazz() {
            return clazz;
        }
    }

    private enum ObjectType {
        INTEGER(Integer.class),
        STRING(String.class),
        BOOLEAN(Boolean.class),
        BIG_DECIMAL(BigDecimal.class),
        LONG(Long.class),
        DOUBLE(Double.class),
        FLOAT(Float.class),
        SHORT(Short.class),
        DEFINITION_OBJECT(DefinitionObject.class),
        STRING_ARRAY(String[].class),
        INTEGER_ARRAY(Integer[].class),
        BOOLEAN_ARRAY(Boolean[].class),
        BIG_DECIMAL_ARRAY(BigDecimal[].class),
        LONG_ARRAY(Long[].class);

        private final Class<?> clazz;

        ObjectType(final Class<?> clazz) {
            this.clazz = clazz;
        }

        public static ObjectType fromClass(final Class<?> clazz) {
            for (final ObjectType type : ObjectType.values()) {
                if (type.clazz == clazz) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid class type for " + clazz.getSimpleName());
        }
    }
}
