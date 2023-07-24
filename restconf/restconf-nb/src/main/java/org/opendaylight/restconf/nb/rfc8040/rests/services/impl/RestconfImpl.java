/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler.REVISION;

import javax.ws.rs.Path;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@Path("/")
public class RestconfImpl implements RestconfService {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();

    private final DatabindProvider databindProvider;

    public RestconfImpl(final DatabindProvider databindProvider) {
        this.databindProvider = requireNonNull(databindProvider);
    }

    @Override
    public NormalizedNodePayload getLibraryVersion() {
        final EffectiveModelContext context = databindProvider.currentContext().modelContext();

        final SchemaInferenceStack stack = SchemaInferenceStack.of(context);
        // FIXME: use rc:data instantiation once the stack supports it
        stack.enterGrouping(Restconf.QNAME);
        stack.enterDataTree(Restconf.QNAME);
        stack.enterDataTree(YANG_LIBRARY_VERSION);

        return NormalizedNodePayload.of(InstanceIdentifierContext.ofStack(stack),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, REVISION.toString()));
    }
}
