/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Provider
public final class ContentParameterProvider implements ParamConverterProvider {
    private static final List<String> POSSIBLE_CONTENT = Arrays.stream(ContentParameter.values())
        .map(ContentParameter::uriValue)
        .collect(Collectors.toUnmodifiableList());

    @Override
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
            final Annotation[] annotations) {
        return rawType.equals(ContentParameter.class) ? new ParamConverter<>() {
            @Override
            public T fromString(final String value) {
                return rawType.cast(RestconfDocumentedException.throwIfNull(ContentParameter.forUriValue(value),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid content parameter: %s, allowed values are %s", value, POSSIBLE_CONTENT));
            }

            @Override
            public String toString(final T value) {
                return ContentParameter.class.cast(value).uriValue();
            }
        } : null;
    }
}
