/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

/**
 * Result of a {@code PUT} request as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>. The definition makes it
 * clear that the logical operation is {@code create-or-replace}.
 */
public enum DataPutResult {
    /**
     * A new resource has been created.
     */
    CREATED,
    /*
     * An existing resources has been replaced.
     */
    REPLACED;
}