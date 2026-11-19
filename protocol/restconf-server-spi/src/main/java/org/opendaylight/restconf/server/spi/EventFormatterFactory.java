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
import org.opendaylight.yangtools.databind.RequestException;

@NonNullByDefault
public abstract non-sealed class EventFormatterFactory<T> extends EventFilterFactory<T> {
    private final EventFormatter<T> emptyFormatter;

    protected EventFormatterFactory(final EventFormatter<T> emptyFormatter) {
        this.emptyFormatter = requireNonNull(emptyFormatter);
    }

    public final EventFormatter<T> getFormatter(final TextParameters textParamaters) {
        return textParamaters.equals(TextParameters.EMPTY) ? emptyFormatter : newFormatter(textParamaters);
    }

    // FIXME: 13.0.0: protected
    public abstract EventFormatter<T> newFormatter(TextParameters textParamaters);

    @Override
    public EventFilter<T> newFilter(final XPathFilterExpression expression) throws RequestException {
        try {
            return newXPathFilter(expression.xpath().getValue());
        } catch (XPathExpressionException e) {
            throw new RequestException("Failed to create filter", e);
        }
    }

    /**
     * Create a new {@link XPathEventFilter}.
     *
     * @param expression XPath expression
     * @return a new {@link XPathEventFilter}
     * @throws XPathExpressionException when the expression fails to compile
     */
    public abstract XPathEventFilter<T> newXPathFilter(String expression) throws XPathExpressionException;
}
