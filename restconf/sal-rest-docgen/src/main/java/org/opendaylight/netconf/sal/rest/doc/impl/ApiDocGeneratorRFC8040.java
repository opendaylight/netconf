/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Objects;
import java.util.Optional;
import org.opendaylight.controller.sal.core.api.model.SchemaService;

/**
 * This class gathers all YANG-defined {@link org.opendaylight.yangtools.yang.model.api.Module}s and
 * generates Swagger compliant documentation for the RFC 8040 version.
 *
 * @author Thomas Pantelis
 */
public class ApiDocGeneratorRFC8040 extends BaseYangSwaggerGeneratorRFC8040 {

    public ApiDocGeneratorRFC8040(SchemaService schemaService) {
        super(Optional.of(Objects.requireNonNull(schemaService)));
    }
}
