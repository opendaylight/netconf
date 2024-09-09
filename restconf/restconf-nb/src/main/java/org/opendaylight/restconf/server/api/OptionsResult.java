/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

/**
 * Result of a {@code OPTIONS} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.1">RFC8040 section 4.1</a>. This enumeration does not list
 * actual HTTP methods, but rather indicates what kind of resource was referenced.
 */
public enum OptionsResult {
    /**
     * An entire data store, which can be modified via {@link RestconfServer}.
     */
    DATASTORE,
    /**
     * A single data resource, which can be modified via {@link RestconfServer}.
     */
    RESOURCE,
    /**
     * A data store or data resource which cannot be modified via {@link RestconfServer}.
     */
    READ_ONLY,
    /**
     * An operation which can be invoked via {@link RestconfServer}.
     */
    OPERATION,
}
