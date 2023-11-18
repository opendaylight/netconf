/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
abstract class AbstractRestconfTest extends AbstractJukeboxTest {
    @Mock
    UriInfo uriInfo;
    @Mock
    AsyncResponse asyncResponse;
    @Mock
    DOMDataBroker dataBroker;
    @Mock
    DOMActionService actionService;
    @Mock
    DOMRpcService rpcService;
    @Mock
    DOMMountPointService mountPointService;
    @Mock
    DOMMountPoint mountPoint;
    @Captor
    ArgumentCaptor<Response> responseCaptor;
    @Captor
    ArgumentCaptor<RestconfDocumentedException> exceptionCaptor;

    JaxRsRestconf restconf;

    @BeforeEach
    final void setupRestconf() {
        restconf = new JaxRsRestconf(new MdsalRestconfServer(() -> DatabindContext.ofModel(modelContext()), dataBroker,
            rpcService, actionService, mountPointService));
    }

    @NonNull EffectiveModelContext modelContext() {
        return JUKEBOX_SCHEMA;
    }
}
