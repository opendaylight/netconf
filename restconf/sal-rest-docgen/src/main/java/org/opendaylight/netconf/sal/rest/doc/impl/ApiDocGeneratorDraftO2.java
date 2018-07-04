/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import java.util.Objects;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

/**
 * This class gathers all YANG-defined {@link org.opendaylight.yangtools.yang.model.api.Module}s and
 * generates Swagger compliant documentation for the bierman draft02 version.
 */
public class ApiDocGeneratorDraftO2 extends BaseYangSwaggerGeneratorDraft02 {

    public ApiDocGeneratorDraftO2(DOMSchemaService schemaService) {
        super(Optional.of(Objects.requireNonNull(schemaService)));
    }
}
