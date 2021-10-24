/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Interface implemented by all Java classes which represent a
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8">RESTCONF query parameter</a>.
 */
// FIXME: sealed when we have JDK17+?
public interface RestconfQueryParam<T extends RestconfQueryParam<T>> extends Immutable {
    /**
     * Return the Java representation class.
     *
     * @return the Java representation class
     */
    @NonNull Class<@NonNull T> javaClass();

    /**
     * Return the URI Request parameter name.
     *
     * @return the URI Request parameter name.
     */
    @NonNull String paramName();
}
