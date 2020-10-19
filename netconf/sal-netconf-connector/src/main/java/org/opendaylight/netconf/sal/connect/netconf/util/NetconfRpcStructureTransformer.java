/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.util.NetconfUtil;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Transforms rpc structures to normalized nodes and vice versa.
 */
class NetconfRpcStructureTransformer implements RpcStructureTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfRpcStructureTransformer.class);

    private final MountPointContext mountContext;

    NetconfRpcStructureTransformer(final MountPointContext mountContext) {
        this.mountContext = mountContext;
    }

    @Override
    public Optional<NormalizedNode<?, ?>> selectFromDataStructure(
            final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> data,
            final YangInstanceIdentifier path) {
        if (data instanceof DOMSourceAnyxmlNode) {
            final NormalizedNodeResult node;
            try {
                node = NetconfUtil.transformDOMSourceToNormalizedNode(mountContext,
                    ((DOMSourceAnyxmlNode)data).getValue());
                return NormalizedNodes.findNode(node.getResult(), path.getPathArguments());
            } catch (final XMLStreamException | URISyntaxException | IOException | SAXException e) {
                LOG.error("Cannot parse anyxml.", e);
                return Optional.empty();
            }
        } else {
            return NormalizedNodes.findNode(data, path.getPathArguments());
        }
    }

    @Override
    public DOMSourceAnyxmlNode createEditConfigStructure(final Optional<NormalizedNode<?, ?>> data,
                                                         final YangInstanceIdentifier dataPath,
                                                         final Optional<ModifyAction> operation) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.createEditConfigAnyxml(mountContext.getEffectiveModelContext(), dataPath,
            operation, data);
    }

    @Override
    public DataContainerChild<?, ?> toFilterStructure(final YangInstanceIdentifier path) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.toFilterStructure(path, mountContext.getEffectiveModelContext());
    }

    @Override
    public DataContainerChild<?, ?> toFilterStructure(final List<FieldsFilter> fieldsFilters) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.toFilterStructure(fieldsFilters, mountContext.getEffectiveModelContext());
    }
}
