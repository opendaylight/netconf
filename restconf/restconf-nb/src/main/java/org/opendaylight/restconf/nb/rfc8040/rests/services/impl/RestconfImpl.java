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
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.Restconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Service for getting the revision of {@code ietf-yang-library}.
 */
@Path("/")
public final class RestconfImpl {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final String YANG_LIBRARY_REVISION = YangLibrary.QNAME.getRevision().orElseThrow().toString();

    private final DatabindProvider databindProvider;

    public RestconfImpl(final DatabindProvider databindProvider) {
        this.databindProvider = requireNonNull(databindProvider);
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
        final EffectiveModelContext context = databindProvider.currentContext().modelContext();

        final SchemaInferenceStack stack = SchemaInferenceStack.of(context);
        // FIXME: use rc:data instantiation once the stack supports it
        stack.enterGrouping(Restconf.QNAME);
        stack.enterDataTree(Restconf.QNAME);
        stack.enterDataTree(YANG_LIBRARY_VERSION);

        return NormalizedNodePayload.of(InstanceIdentifierContext.ofStack(stack),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, YANG_LIBRARY_REVISION));
    }
}
