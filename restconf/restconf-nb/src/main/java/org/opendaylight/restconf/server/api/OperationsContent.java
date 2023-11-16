/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 *
 * @deprecated NETCONF-773: This enumeration should not be necessary, but the implemntation looks at actions as well,
 *                          and also work for yang-ext:mount -- which means we have mixed outer/inner schema
 */
@Deprecated(since = "7.0.0", forRemoval = true)
public enum OperationsContent {
    JSON,
    XML;
}
