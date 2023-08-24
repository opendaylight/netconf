/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import javax.ws.rs.HttpMethod;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;

public final class PostEntity extends OperationEntity {
    private static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    private static final String INPUT_SUFFIX = "_input";

    public PostEntity(final OperationDefinition schema, final String deviceName, final String moduleName) {
        super(schema, deviceName, moduleName);
    }

    protected String operation() {
        return "post";
    }

    @Nullable String summary() {
        final var operationName = schema().getQName().getLocalName() + INPUT_SUFFIX;
        return SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName(), moduleName(), operationName);
    }
}
