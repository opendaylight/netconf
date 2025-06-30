/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.ConfigNodeKey;
import org.opendaylight.netconf.client.mdsal.util.NormalizedDataUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Transforms rpc structures to normalized nodes and vice versa.
 */
class NetconfRpcStructureTransformer implements RpcStructureTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfRpcStructureTransformer.class);

    private final DatabindContext databind;

    NetconfRpcStructureTransformer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    @Override
    public Optional<NormalizedNode> selectFromDataStructure(final DataContainerChild data,
            final YangInstanceIdentifier path) {
        if (data instanceof DOMSourceAnyxmlNode anyxml) {
            final NormalizationResultHolder node;
            try {
                node = NormalizedDataUtil.transformDOMSourceToNormalizedNode(databind.mountContext(), anyxml.body());
                return NormalizedNodes.findNode(node.getResult().data(), path.getPathArguments());
            } catch (final XMLStreamException | URISyntaxException | IOException | SAXException e) {
                LOG.error("Cannot parse anyxml.", e);
                return Optional.empty();
            }
        } else {
            return NormalizedNodes.findNode(data, path.getPathArguments());
        }
    }

    @Override
    public AnyxmlNode<DOMSource> createEditConfigStructure(final Optional<NormalizedNode> data,
            final YangInstanceIdentifier dataPath, final Optional<EffectiveOperation> operation) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.createEditConfigAnyxml(databind.modelContext(), dataPath, operation,
            data);
    }

    @Override
    public AnyxmlNode<DOMSource> createEditConfigStructure(
            final Map<ConfigNodeKey, Optional<NormalizedNode>> elements) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.createEditConfigAnyxml(databind.modelContext(), elements);
    }

    @Override
    public AnyxmlNode<?> toFilterStructure(final YangInstanceIdentifier path) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.toFilterStructure(path, databind.modelContext());
    }

    @Override
    public AnyxmlNode<?> toFilterStructure(final List<FieldsFilter> fieldsFilters) {
        // FIXME: propagate MountPointContext
        return NetconfMessageTransformUtil.toFilterStructure(fieldsFilters, databind.modelContext());
    }
}
