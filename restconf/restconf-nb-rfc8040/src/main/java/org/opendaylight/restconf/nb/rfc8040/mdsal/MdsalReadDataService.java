/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.mdsal;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataResponse;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataService;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReadDataService} implemented on top of {@link DOMDataTreeReadTransaction}.
 */
@Component
@Singleton
// TODO: this should live in its own artifact
public final class MdsalReadDataService implements ReadDataService {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalReadDataService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private final DOMMountPointService mountPointService;
    private final DOMDataBroker dataBroker;
    private final DOMSchemaService schema;

    @Inject
    @Activate
    public MdsalReadDataService(final @Reference DOMSchemaService schema, final @Reference DOMDataBroker dataBroker,
            final @Reference DOMMountPointService mountPointService) {
        this.schema = requireNonNull(schema);
        this.dataBroker = requireNonNull(dataBroker);
        this.mountPointService = requireNonNull(mountPointService);
    }

    @Override
    public CompletionStage<ReadDataResponse> readData(final ApiPath path, final ReadDataParams parameters) {
        // Acquire a Context reference
        final var schemaContextRef = schema.getGlobalContext();

        // FIXME: determine whether this is a local apiPath or not
        //        - if it is not, talk to mountPoint service and forward the request
        //        - if it is, binding to current schema, allocate a read transaction and execute it on top of it

        final var localContext = LocalContext.ofRead(path, schemaContextRef);
        final var remotePath = localContext.remotePath();
        if (remotePath != null) {
            final var mountPoint = mountPointService.getMountPoint(localContext.localPath()).orElseThrow(() -> {
                LOG.warn("Mount point {} does not expose a suitable access interface", mountPoint.getIdentifier());
                return new RestconfDocumentedException("Could not find a supported access interface in mount point "
                    + mountPoint.getIdentifier());
            });

            // FIXME: create a delegate service and dispatch to it
            //          final RestconfStrategy strategy = getRestconfStrategy(mountPoint);
            final var delegate = (ReadDataService) null;
            return delegate.readData(localContext.remotePath(), parameters);
        }

        final var fields = parameters.fields();
        final var queryParams = fields == null ? QueryParameters.of(parameters)
            // FIXME: acquire context schema node from LocalContext
            : QueryParameters.ofFields(parameters, WriterFieldsTranslator.translate(identifier, fields));
        final var localPath = localContext.localPath();

        // FIXME: 'strategy' amounts to 'this', inline this call
        final NormalizedNode node = ReadDataTransactionUtil.readData(parameters.content(), localPath, strategy,
            parameters.withDefaults(), schemaContextRef);
        if (node == null) {
            return CompletableFuture.failedStage(new RestconfDocumentedException(
                "Request could not be completed because the relevant data model content does not exist",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING));
        }

        // Response headers
        final Map<String, String> headers;
        final NormalizedNodePayload payload;
        switch (parameters.content()) {
            case ALL:
            case CONFIG:
                final QName type = node.getIdentifier().getNodeType();
                headers = Map.of(
                    "ETag",
                    '"' + type.getModule().getRevision().map(Revision::toString).orElse(null)
                    + "-" + type.getLocalName() + '"',
                    "Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())));
                // FIXME: use localContext
                payload = NormalizedNodePayload.ofReadData(instanceIdentifier, node, queryParams);
                break;
            default:
                headers = Map.of();
                // FIXME: use localContext
                payload = NormalizedNodePayload.ofReadData(instanceIdentifier, node, queryParams);
        }

        return CompletableFuture.completedStage(new ReadDataResponse.OfNormalizedNode(payload));
    }
}
