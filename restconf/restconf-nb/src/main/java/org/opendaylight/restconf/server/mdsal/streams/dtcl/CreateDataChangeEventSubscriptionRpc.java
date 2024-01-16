/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.dtcl;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker.DataTreeChangeExtension;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierSerializer;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscription;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.CreateDataChangeEventSubscriptionInput1;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link CreateDataChangeEventSubscription}.
 */
@Singleton
@Component
public final class CreateDataChangeEventSubscriptionRpc extends RpcImplementation {
    private static final @NonNull NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(
        QName.create(CreateDataChangeEventSubscriptionInput1.QNAME, "datastore").intern());
    private static final @NonNull NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());
    private static final @NonNull NodeIdentifier PATH_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionInput.QNAME, "path").intern());
    private static final @NonNull NodeIdentifier OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);

    private final DatabindProvider databindProvider;
    private final DataTreeChangeExtension changeService;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public CreateDataChangeEventSubscriptionRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final DatabindProvider databindProvider, @Reference final DOMDataBroker dataBroker) {
        super(CreateDataChangeEventSubscription.QNAME);
        this.databindProvider = requireNonNull(databindProvider);
        changeService = dataBroker.extension(DataTreeChangeExtension.class);
        if (changeService == null) {
            throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService");
        }
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param input Input of RPC - example in JSON (data-change-event stream):
     *              <pre>
     *              {@code
     *                  {
     *                      "input": {
     *                          "path": "/toaster:toaster/toaster:toasterStatus",
     *                          "sal-remote-augment:datastore": "OPERATIONAL",
     *                      }
     *                  }
     *              }
     *              </pre>
     * @return Future output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    @Override
    public RestconfFuture<OperationsPostResult> invoke(final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final var datastoreName = leaf(body, DATASTORE_NODEID, String.class);
        final var datastore = datastoreName != null ? LogicalDatastoreType.valueOf(datastoreName)
            : LogicalDatastoreType.CONFIGURATION;

        final var path = leaf(body, PATH_NODEID, YangInstanceIdentifier.class);
        if (path == null) {
            return RestconfFuture.failed(
                new RestconfDocumentedException("missing path", ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT));
        }

        return streamRegistry.createStream(restconfURI,
            new DataTreeChangeSource(databindProvider, changeService, datastore, path),
            "Events occuring in " + datastore + " datastore under /"
                + new YangInstanceIdentifierSerializer(input.databind()).serializePath(path))
            .transform(stream -> input.newOperationOutput(Builders.containerBuilder()
                .withNodeIdentifier(OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }
}
