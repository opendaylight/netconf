/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for streams
 *
 * <ul>
 * <li>create stream
 * <li>subscribe
 * </ul>
 *
 */
public final class CreateStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStreamUtil.class);

    private CreateStreamUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Create stream with POST operation via RPC
     *
     * @param payload
     *            - input of rpc - example in JSON:
     *
     *            <pre>
     *            {@code
     *            {
     *                "input": {
     *                    "path": "/toaster:toaster/toaster:toasterStatus",
     *                    "sal-remote-augment:datastore": "OPERATIONAL",
     *                    "sal-remote-augment:scope": "ONE"
     *                }
     *            }
     *            }
     *            </pre>
     *
     * @param refSchemaCtx
     *            - reference to {@link SchemaContext} -
     *            {@link SchemaContextRef}
     * @return {@link CheckedFuture} with {@link DOMRpcResult} - This mean
     *         output of RPC - example in JSON:
     *
     *         <pre>
     *         {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *         }
     *         </pre>
     *
     */
    public static DOMRpcResult createStream(final NormalizedNodeContext payload,
            final SchemaContextRef refSchemaCtx) {
        final ContainerNode data = (ContainerNode) payload.getData();
        final QName qname = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final YangInstanceIdentifier path = preparePath(payload, data, qname);
        final String streamName = prepareStream(path, refSchemaCtx.get(), data);

        final QName outputQname = QName.create(qname, "output");
        final QName streamNameQname = QName.create(qname, "stream-name");

        final ContainerNode output = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new NodeIdentifier(outputQname))
                .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existListenerFor(streamName)) {
            Notificator.createListener(path, streamName);
        }

        return new DefaultDOMRpcResult(output);
    }

    private static String prepareStream(final YangInstanceIdentifier path, final SchemaContext schemaContext,
            final ContainerNode data) {
        LogicalDatastoreType ds = parseEnum(data, LogicalDatastoreType.class,
                RestconfStreamsConstants.DATASTORE_PARAM_NAME);
        ds = ds == null ? RestconfStreamsConstants.DEFAULT_DS : ds;

        DataChangeScope scope = parseEnum(data, DataChangeScope.class, RestconfStreamsConstants.SCOPE_PARAM_NAME);
        scope = scope == null ? RestconfStreamsConstants.DEFAULT_SCOPE : scope;

        final String streamName = Notificator
                .createStreamNameFromUri(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext)
                + RestconfStreamsConstants.DS_URI + ds + RestconfStreamsConstants.SCOPE_URI + scope);
        if((streamName == null) || streamName.equals("")){
            final String errMsg = "Path is empty or contains value node which is not Container or List build-in type.";
            LOG.debug(errMsg + path);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        return streamName;
    }

    private static <T> T parseEnum(final ContainerNode data, final Class<T> clazz, final String paramName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> augNode = data
                .getChild(RestconfStreamsConstants.SAL_REMOTE_AUG_IDENTIFIER);
        if (!augNode.isPresent() && !(augNode instanceof AugmentationNode)) {
            return null;
        }
        final Optional<DataContainerChild<? extends PathArgument, ?>> enumNode =
                ((AugmentationNode) augNode.get()).getChild(
                        new NodeIdentifier(QName.create(RestconfStreamsConstants.SAL_REMOTE_AUGMENT, paramName)));
        if (!enumNode.isPresent()) {
            return null;
        }
        final Object value = enumNode.get().getValue();
        if (!(value instanceof String)) {
            return null;
        }

        return StreamUtil.resolveEnum(clazz, (String) value);
    }

    private static YangInstanceIdentifier preparePath(final NormalizedNodeContext payload, final ContainerNode data,
            final QName qName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> path = data
                .getChild(new YangInstanceIdentifier.NodeIdentifier(QName.create(qName, "path")));
        Object pathValue = null;
        if (path.isPresent()) {
            pathValue = path.get().getValue();
        }
        if (!(pathValue instanceof YangInstanceIdentifier)) {
            final String errMsg = "Instance identifier was not normalized correctly ";
            LOG.debug(errMsg + qName);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }
        return (YangInstanceIdentifier) pathValue;
    }
}
