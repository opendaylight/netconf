/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

public final class DefinitionObject {
//   "toaster2_toaster_toasterSlot_config_slotInfo": {   <-- Root node
//        "properties": {                                <-- Nodes with parent
//            "numberOfToastPrepared": {
//                "description": "",
//                "format": "int64",
//                "default": 0,
//                "type": "integer",
//                "xml": {
//                    "name": "numberOfToastPrepared",
//                    "namespace": "http://netconfcentral.org/ns/toaster/augmented"
//                }
//            }
//        },
//        "type": "object",
//        "title": "toaster2_toaster_toasterSlot_config_slotInfo",
//        "description": "",
//        "xml": {
//            "name": "slotInfo",
//            "namespace": "http://netconfcentral.org/ns/toaster/augmented"
//        }
//    },
    private final Map<String, DataType<?>> data;
    private final DefinitionObject parent;

    public DefinitionObject() {
        this.parent = null;
        // The root node contains a large number of nodes that are stored inside the data.
        this.data = new HashMap<>(1000, 0.85f);
    }

    public DefinitionObject(final DefinitionObject parent) {
        this.parent = parent.getParent().orElse(parent);
        // The nodes that are not in the root contain a small number of nodes, usually 6 or fewer.
        // The initial size of the Map is set to 9 elements to ensure that it does not reach the limit
        // where it doubles size. (9*0.75=6.75)
        this.data = new HashMap<>(9);
    }

    public Map<String, DataType<?>> getData() {
        return data;
    }

    public Optional<DefinitionObject> getParent() {
        return Optional.ofNullable(parent);
    }

    public void addData(final String key, final String value) {
        data.put(key, new DataType<>(value, String.class));
    }

    public void addData(final String key, final int value) {
        data.put(key, new DataType<>(value, int.class));
    }

    public void addData(final String key, final long value) {
        data.put(key, new DataType<>(value, long.class));
    }

    public void addData(final String key, final double value) {
        data.put(key, new DataType<>(value, double.class));
    }

    public void addData(final String key, final float value) {
        data.put(key, new DataType<>(value, float.class));
    }

    public void addData(final String key, final short value) {
        data.put(key, new DataType<>(value, short.class));
    }

    public void addData(final String key, final BigDecimal value) {
        data.put(key, new DataType<>(value, BigDecimal.class));
    }

    public void addData(final String key, final boolean value) {
        data.put(key, new DataType<>(value, boolean.class));
    }

    public void addData(final String key, final String[] value) {
        data.put(key, new DataType<>(value, String[].class));
    }

    public void addData(final String key, final int[] value) {
        data.put(key, new DataType<>(value, int[].class));
    }

    public void addData(final String key, final long[] value) {
        data.put(key, new DataType<>(value, long[].class));
    }

    public void addData(final String key, final BigDecimal[] value) {
        data.put(key, new DataType<>(value, BigDecimal[].class));
    }

    public void addData(final String key, final boolean[] value) {
        data.put(key, new DataType<>(value, boolean[].class));
    }

    public void addData(final String key, final DefinitionObject value) {
        data.put(key, new DataType<>(value, DefinitionObject.class));
    }

    public void addData(final String key, final DataType<?> value) {
        data.put(key, value);
    }


    public DefinitionObject getRoot() {
        return Objects.requireNonNullElse(parent, this);
    }

    public ObjectNode convertToObjectNode() {
        final ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (final Entry<String, DataType<?>> entry : data.entrySet()) {
            final DataType<?> dataType = entry.getValue();
            switch (dataType.getClazz()) {
                case INT -> {
                    result.put(entry.getKey(), (int) dataType.getValue());
                }
                case STRING -> {
                    result.put(entry.getKey(), (String) dataType.getValue());
                }
                case BOOLEAN -> {
                    result.put(entry.getKey(), (boolean) dataType.getValue());
                }
                case BIG_DECIMAL -> {
                    result.put(entry.getKey(), (BigDecimal) dataType.getValue());
                }
                case LONG -> {
                    result.put(entry.getKey(), (long) dataType.getValue());
                }
                case DOUBLE -> {
                    result.put(entry.getKey(), (double) dataType.getValue());
                }
                case FLOAT -> {
                    result.put(entry.getKey(), (float) dataType.getValue());
                }
                case SHORT -> {
                    result.put(entry.getKey(), (short) dataType.getValue());
                }
                case DEFINITION_OBJECT -> {
                    final DefinitionObject value = (DefinitionObject) dataType.getValue();
                    result.set(entry.getKey(), value.convertToObjectNode());
                }
                case STRING_ARRAY -> {
                    final ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    final String[] value = (String[]) dataType.getValue();
                    for (final String val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case INT_ARRAY -> {
                    final ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    final int[] value = (int[]) dataType.getValue();
                    for (final int val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case BOOLEAN_ARRAY -> {
                    final ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    final boolean[] value = (boolean[]) dataType.getValue();
                    for (final boolean val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case BIG_DECIMAL_ARRAY -> {
                    final ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    final BigDecimal[] value = (BigDecimal[]) dataType.getValue();
                    for (final BigDecimal val : value) {
                        jsonNodes.add(val);
                    }
                    result.set(entry.getKey(), jsonNodes);
                }
                case LONG_ARRAY -> {
                    final ArrayNode jsonNodes = JsonNodeFactory.instance.arrayNode();
                    final long[] value = (long[]) dataType.getValue();
                    for (final long val : value) {
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

    public void writeDataToJsonGenerator(final JsonGenerator gen) throws IOException {
        for (final Entry<String, DataType<?>> entry : data.entrySet()) {
            final DataType<?> dataType = entry.getValue();
            switch (dataType.getClazz()) {
                case STRING -> {
                    gen.writeStringField(entry.getKey(), (String) dataType.getValue());
                }
                case BOOLEAN -> {
                    gen.writeBooleanField(entry.getKey(), (boolean) dataType.getValue());
                }
                case INT -> {
                    gen.writeNumberField(entry.getKey(), (int) dataType.getValue());
                }
                case BIG_DECIMAL -> {
                    gen.writeNumberField(entry.getKey(), (BigDecimal) dataType.getValue());
                }
                case LONG -> {
                    gen.writeNumberField(entry.getKey(), (long) dataType.getValue());
                }
                case DOUBLE -> {
                    gen.writeNumberField(entry.getKey(), (double) dataType.getValue());
                }
                case FLOAT -> {
                    gen.writeNumberField(entry.getKey(), (float) dataType.getValue());
                }
                case SHORT -> {
                    gen.writeNumberField(entry.getKey(), (short) dataType.getValue());
                }
                case DEFINITION_OBJECT -> {
                    gen.writeObjectFieldStart(entry.getKey());
                    ((DefinitionObject) dataType.getValue()).writeDataToJsonGenerator(gen);
                    gen.writeEndObject();
                }
                case STRING_ARRAY -> {
                    gen.writeArrayFieldStart(entry.getKey());
                    final String[] value = (String[]) dataType.getValue();
                    gen.writeArray(value, 0, value.length);
                    gen.writeEndArray();
                }
                case INT_ARRAY -> {
                    gen.writeArrayFieldStart(entry.getKey());
                    final int[] value = (int[]) dataType.getValue();
                    for (final int val : value) {
                        gen.writeNumber(val);
                    }
                    gen.writeEndArray();
                }
                case BOOLEAN_ARRAY -> {
                    gen.writeArrayFieldStart(entry.getKey());
                    final boolean[] value = (boolean[]) dataType.getValue();
                    for (final boolean val : value) {
                        gen.writeBoolean(val);
                    }
                    gen.writeEndArray();
                }
                case BIG_DECIMAL_ARRAY -> {
                    gen.writeArrayFieldStart(entry.getKey());
                    final BigDecimal[] value = (BigDecimal[]) dataType.getValue();
                    for (final BigDecimal val : value) {
                        gen.writeNumber(val);
                    }
                    gen.writeEndArray();
                }
                case LONG_ARRAY -> {
                    gen.writeArrayFieldStart(entry.getKey());
                    final long[] value = (long[]) dataType.getValue();
                    for (final long val : value) {
                        gen.writeNumber(val);
                    }
                    gen.writeEndArray();
                }
                default -> {
                }
            }
        }
    }

    public static final class DataType<T> {
        private final T value;
        private final ObjectType clazz;

        private DataType(final T value, final Class<T> clazz) {
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
        INT(int.class),
        STRING(String.class),
        BOOLEAN(boolean.class),
        BIG_DECIMAL(BigDecimal.class),
        LONG(long.class),
        DOUBLE(double.class),
        FLOAT(float.class),
        SHORT(short.class),
        DEFINITION_OBJECT(DefinitionObject.class),
        STRING_ARRAY(String[].class),
        INT_ARRAY(int[].class),
        BOOLEAN_ARRAY(boolean[].class),
        BIG_DECIMAL_ARRAY(BigDecimal[].class),
        LONG_ARRAY(long[].class);

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
