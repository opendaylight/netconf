/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;

public abstract class EventFormatterFactory<T> {
    private final @NonNull EventFormatter<T> emptyFormatter;

    protected EventFormatterFactory(final EventFormatter<T> emptyFormatter) {
        this.emptyFormatter = requireNonNull(emptyFormatter);
    }

    public final @NonNull EventFormatter<T> getFormatter(final @NonNull TextParameters textParamaters) {
        return textParamaters.equals(TextParameters.EMPTY) ? emptyFormatter : newFormatter(textParamaters);
    }

    public abstract @NonNull EventFormatter<T> getFormatter(@NonNull TextParameters textParamaters, String xpathFilter)
        throws XPathExpressionException;

    public abstract @NonNull EventFormatter<T> newFormatter(@NonNull TextParameters textParamaters);
}
