/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathExpression;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathMathMode;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathParserFactory;

/**
 * This class represents a {@code filter} parameter as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.4">RFC8040 section 4.8.4</a>.
 */
@NonNullByDefault
public final class FilterParameter implements Immutable {
    private static final URI CAPABILITY = URI.create("urn:ietf:params:restconf:capability:filter:1.0");

    private final YangXPathExpression value;

    private FilterParameter(final YangXPathExpression value) {
        this.value = requireNonNull(value);
    }

    public static FilterParameter forUriValue(final YangXPathParserFactory parserFactory, final String uriValue)
            throws XPathExpressionException {
        return new FilterParameter(parserFactory.newParser(YangXPathMathMode.EXACT).parseExpression(uriValue));
    }

    public YangXPathExpression value() {
        return value;
    }

    public static String uriName() {
        return "filter";
    }

    public static URI capabilityUri() {
        return CAPABILITY;
    }
}
