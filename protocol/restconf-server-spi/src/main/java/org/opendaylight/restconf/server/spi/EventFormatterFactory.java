/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public abstract class EventFormatterFactory<T> {
    private final EventFormatter<T> emptyFormatter;

    protected EventFormatterFactory(final EventFormatter<T> emptyFormatter) {
        this.emptyFormatter = requireNonNull(emptyFormatter);
    }

    public final EventFormatter<T> getFormatter(final TextParameters textParamaters) {
        return textParamaters.equals(TextParameters.EMPTY) ? emptyFormatter : newFormatter(textParamaters);
    }

    public abstract EventFormatter<T> newFormatter(TextParameters textParamaters);

    /**
     * Create a new {@link XPathEventFilter}.
     *
     * @param expression XPath expression
     * @return a new {@link XPathEventFilter}
     * @throws XPathExpressionException when the expression fails to compile
     */
    public abstract XPathEventFilter<T> newXPathFilter(String expression) throws XPathExpressionException;
}
