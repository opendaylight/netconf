/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.handlers;

/**
 * Handler for handling object prepared by provider for Restconf services.
 *
 * @param <T>
 *             specific type go object for handling it
 */
interface Handler<T> {

    /**
     * Get prepared object.
     *
     * @return T
     */
    T get();

    /**
     * Update object.
     *
     * @param object
     *             new object to update old object
     */
    default void update(T object) {}
}
