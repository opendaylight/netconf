/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * Service for getting the revision of {@code ietf-yang-library}.
 */
@Path("/")
public final class RestconfImpl {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final String YANG_LIBRARY_REVISION = YangLibrary.QNAME.getRevision().orElseThrow().toString();

    private final MdsalRestconfServer server;

    public RestconfImpl(final MdsalRestconfServer server) {
        this.server = requireNonNull(server);
    }

    /**
     * Get revision of IETF YANG Library module.
     *
     * @return {@link NormalizedNodePayload}
     */
    @GET
    @Path("/yang-library-version")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public NormalizedNodePayload getLibraryVersion() {
        final var stack = server.bindRequestRoot().inference().toSchemaInferenceStack();
        stack.enterYangData(YangApi.NAME);
        stack.enterDataTree(Restconf.QNAME);
        stack.enterDataTree(YANG_LIBRARY_VERSION);

        return new NormalizedNodePayload(stack.toInference(),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, YANG_LIBRARY_REVISION));
    }
}
