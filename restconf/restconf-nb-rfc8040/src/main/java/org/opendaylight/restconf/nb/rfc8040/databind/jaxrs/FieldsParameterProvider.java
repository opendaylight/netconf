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
import java.text.ParseException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

@Provider
public final class FieldsParameterProvider implements ParamConverterProvider {
    @Override
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
            final Annotation[] annotations) {
        return rawType.equals(FieldsParameter.class) ? new ParamConverter<>() {
            @Override
            public T fromString(final String value) {
                try {
                    return rawType.cast(FieldsParameter.parse(value));
                } catch (ParseException e) {
                    throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                        ErrorTag.INVALID_VALUE, "Invalid filds parameter: " + value));
                }
            }

            @Override
            public String toString(final T value) {
                return FieldsParameter.class.cast(value).uriValue();
            }
        } : null;
    }
}
