/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.Restconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Data;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Operations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@Path("/")
public class RestconfImpl implements RestconfService {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    //TODO this one doesn't work - keep getting error about operations not present inside ietf-restconf
    private static final String OPERATIONS_IDENTIFIER = "network-topology:network-topology/topology=topology-netconf/"
            + "node=17830-sim-device/yang-ext:mount";

    private final DatabindProvider databindProvider;
    private final RestconfDataService dataService;
    private final RestconfOperationsService operationsService;

//    public RestconfImpl(final DatabindProvider databindProvider) {
//        this.databindProvider = requireNonNull(databindProvider);
//    }

    public RestconfImpl(final DatabindProvider databindProvider, final RestconfDataService dataService,
                        final RestconfOperationsService operationsService) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataService = requireNonNull(dataService);
        this.operationsService = requireNonNull(operationsService);
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
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, IetfYangLibrary.REVISION.toString()));
    }

    @Override
    public Response readDataRoot(final UriInfo uriInfo) {
        final var context = databindProvider.currentContext().modelContext();
        final var stack = SchemaInferenceStack.of(context);
        stack.enterGrouping(Restconf.QNAME);
        stack.enterDataTree(Restconf.QNAME);
        final var identifier = InstanceIdentifierContext.ofStack(stack);

        // TODO combine data, operations and yang library version into {@code data} below
        final var libVersion = getLibraryVersion().getData();
        final var data = ((NormalizedNodePayload) dataService.readData(uriInfo).getEntity());
        // This one is only for certain device. getOperationsJson/Xml - is what we need.
        final var operations = operationsService.getOperations(OPERATIONS_IDENTIFIER, uriInfo);

        final var result = Builders.containerBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Restconf.QNAME))
                .withChild(Builders.containerBuilder()
                        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Data.QNAME))
                        .withChild((DataContainerChild) data.getData())
                        .build())
                .withChild(Builders.containerBuilder()
                        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Operations.QNAME))
                        .withChild((DataContainerChild) operations.getData())
                        .build())
                .withChild(((LeafNode) libVersion))
                .build();
        return Response.status(Response.Status.OK).entity(NormalizedNodePayload.of(identifier, result)).build();
    }
}
