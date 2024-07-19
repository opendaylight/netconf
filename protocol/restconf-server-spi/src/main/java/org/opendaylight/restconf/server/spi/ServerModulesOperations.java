/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;

/**
 * Baseline operations for acquiring model source.
 */
@Beta
@NonNullByDefault
public interface ServerModulesOperations {

    void getModelSource(ServerRequest<ModulesGetResult> request, SourceIdentifier source,
        Class<? extends SourceRepresentation> representation);
}
