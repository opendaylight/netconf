/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Xpath10;
import org.opendaylight.yangtools.databind.DatabindContext;

/**
 * A {@link FilterExpression} based on an XPath expression.
 *
 * @since 12.0.3
 */
// FIXME: hold an unbound YangXPathExpression.UnqualifiedBound instead of an Xpath10
@NonNullByDefault
public record XPathFilterExpression(DatabindContext databind, Xpath10 xpath) implements FilterExpression {
    public XPathFilterExpression {
        requireNonNull(databind);
        requireNonNull(xpath);
    }
}
