/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Query parameters of a RESTCONF request URI. Individual parameters can be looked up by
 * {@link RestconfQueryParam#paramName()} via {@link #lookup(String)}. All parameters are accessible via
 * {@link #asCollection()}, where each {@link RestconfQueryParam#paramName()} is guaranteed to be encountered at most
 * once.
 */
@NonNullByDefault
public interface QueryParameters extends Immutable {

    Collection<? extends Entry<String, String>> asCollection();

    @Nullable String lookup(String paramName);

    <T extends RestconfQueryParam<T>> T getDefault(Class<T> javaClass) throws NoSuchElementException;
}
