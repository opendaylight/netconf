/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.databind.DatabindContext;

/**
 * An expression that can be used to filter data in its domain-specific object model.
 *
 * @since 12.0.3
 */
@NonNullByDefault
public sealed interface FilterExpression permits SubtreeFilterExpression, XPathFilterExpression {
    /**
     * {@return the {@link DatabindContext} used to create this expression}
     */
    DatabindContext databind();
}
