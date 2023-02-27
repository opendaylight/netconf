/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectMapperInitializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectMapperInitializer() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
