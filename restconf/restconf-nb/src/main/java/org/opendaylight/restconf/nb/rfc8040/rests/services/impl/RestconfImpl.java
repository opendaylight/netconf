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
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfOperationsService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

@Path("/")
public class RestconfImpl implements RestconfService, RestconfDataService, RestconfOperationsService {
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();

    private final DatabindProvider databindProvider;
    private final RestconfDataService dataService;
    private final RestconfOperationsService operationsService;

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

        final var libVersion = getLibraryVersion().getData();
        final var data = ((NormalizedNodePayload) readData(uriInfo).getEntity()).getData();
        final var operations = getOperations(null, uriInfo).getData(); // FIXME what to do with null?

        final var result = Builders.containerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(Restconf.QNAME))
                .withChild((LeafNode) libVersion)
                .withChild((ContainerNode) data)
                .withChild((ContainerNode) operations)
                .build();
        return Response.status(Response.Status.OK).entity(NormalizedNodePayload.of(identifier, result)).build();
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        return dataService.readData(identifier, uriInfo);
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return dataService.readData(uriInfo);
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodePayload payload, final UriInfo uriInfo) {
        return dataService.putData(identifier, payload, uriInfo);
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodePayload payload, final UriInfo uriInfo) {
        return dataService.postData(identifier, payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodePayload payload, final UriInfo uriInfo) {
        return dataService.postData(payload, uriInfo);
    }

    @Override
    public Response deleteData(final String identifier) {
        return dataService.deleteData(identifier);
    }

    @Override
    public PatchStatusContext patchData(final String identifier, final PatchContext context, final UriInfo uriInfo) {
        return dataService.patchData(identifier, context, uriInfo);
    }

    @Override
    public PatchStatusContext patchData(final PatchContext context, final UriInfo uriInfo) {
        return dataService.patchData(context, uriInfo);
    }

    @Override
    public Response patchData(final String identifier, final NormalizedNodePayload payload, final UriInfo uriInfo) {
        return dataService.patchData(identifier, payload, uriInfo);
    }

    @Override
    public String getOperationsJSON() {
        return operationsService.getOperationsJSON();
    }

    @Override
    public String getOperationsXML() {
        return operationsService.getOperationsXML();
    }

    @Override
    public NormalizedNodePayload getOperations(final String identifier, final UriInfo uriInfo) {
        return operationsService.getOperations(identifier, uriInfo);
    }
}
