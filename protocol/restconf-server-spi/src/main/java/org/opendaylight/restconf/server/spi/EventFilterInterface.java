/* Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public interface EventFilterInterface<T> {
    /**
     * @param modelContext context for any XML‐serialization you need
     * @param event        the thing you’re filtering on
     * @return true if it passes
     */
    boolean matches(EffectiveModelContext modelContext, T event);
}