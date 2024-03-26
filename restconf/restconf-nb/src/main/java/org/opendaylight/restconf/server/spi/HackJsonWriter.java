/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodec;

/**
 * A hack to intercept {@link JSONCodec#writeValue(JsonWriter, Object)} output. This class is closely tailored to
 * respond implementation behaviour of JSONCodecs.
 */
// FIXME: remove this class once we have YANGTOOLS-1569
final class HackJsonWriter extends JsonWriter {
    record Value(String rawString, Kind kind) {
        enum Kind {
            BOOLEAN,
            NULL,
            NUMBER,
            STRING
        }

        Value {
            requireNonNull(rawString);
            requireNonNull(kind);
        }
    }

    private static final Value FALSE = new Value("false", Value.Kind.BOOLEAN);
    private static final Value TRUE = new Value("true", Value.Kind.BOOLEAN);
    private static final Value NULL = new Value("[null]", Value.Kind.NULL);

    private Value captured = null;

    HackJsonWriter() {
        super(new StringWriter());
    }

    @Override
    public JsonWriter nullValue() throws IOException {
        capture(NULL);
        return super.nullValue();
    }

    @Override
    public JsonWriter value(final boolean value) throws IOException {
        capture(value ? TRUE : FALSE);
        return super.value(value);
    }

    @Override
    public JsonWriter value(final Boolean value) throws IOException {
        // We assume non-null values
        return value(value.booleanValue());
    }

    @Override
    public JsonWriter value(final Number value) throws IOException {
        capture(new Value(value.toString(), Value.Kind.NUMBER));
        return super.value(value);
    }

    @Override
    public JsonWriter value(final String value) throws IOException {
        if (value == null) {
            return nullValue();
        }
        capture(new Value(value, Value.Kind.STRING));
        return super.value(value);
    }

    @NonNull Value acquireCaptured() throws IOException {
        final var local = captured;
        if (local == null) {
            throw new IOException("No value set");
        }
        return local;
    }

    private void capture(final Value newValue) throws IOException {
        if (captured != null) {
            throw new IOException("Value already set to " + captured);
        }
        captured = newValue;
    }
}
