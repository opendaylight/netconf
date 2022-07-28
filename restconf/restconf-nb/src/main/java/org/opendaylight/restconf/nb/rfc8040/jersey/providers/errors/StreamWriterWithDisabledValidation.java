/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import java.io.IOException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.ForwardingNormalizedNodeStreamWriter;

/**
 * Created delegating writer to special-case error-info as error-info is defined as an empty container in the restconf
 * yang schema but we create a leaf node so we can output it. The delegate stream writer validates the node type against
 * the schema and thus will expect a LeafSchemaNode but the schema has a ContainerSchemaNode so, to avoid an error,
 * we override the leafNode behavior for error-info.
 */
abstract class StreamWriterWithDisabledValidation extends ForwardingNormalizedNodeStreamWriter {

    private final QName excludedQName;
    private boolean inOurLeaf;

    /**
     * Creation of the {@link NormalizedNode} stream-writer with {@link QName} that is excluded from type-check.
     *
     * @param excludedQName QName of the element that is excluded from type-check.
     */
    StreamWriterWithDisabledValidation(final QName excludedQName) {
        this.excludedQName = excludedQName;
    }

    @Override
    public void startLeafNode(final NodeIdentifier name) throws IOException {
        if (name.getNodeType().equals(excludedQName)) {
            inOurLeaf = true;
            startLeafNodeWithDisabledValidation(name);
        } else {
            super.startLeafNode(name);
        }
    }

    /**
     * Writing of the excluded leaf to the output stream.
     *
     * @param nodeIdentifier Node identifier of the leaf to be written to output stream.
     * @throws IOException Writing of the leaf to output stream failed.
     */
    abstract void startLeafNodeWithDisabledValidation(NodeIdentifier nodeIdentifier) throws IOException;

    @Override
    public void scalarValue(final Object value) throws IOException {
        if (inOurLeaf) {
            scalarValueWithDisabledValidation(value);
        } else {
            super.scalarValue(value);
        }
    }

    /**
     * Writing of the value of the excluded leaf to the output stream.
     *
     * @param value Value of the excluded leaf.
     * @throws IOException Writing of the leaf value to the output stream failed.
     */
    abstract void scalarValueWithDisabledValidation(Object value) throws IOException;

    @Override
    public void endNode() throws IOException {
        if (inOurLeaf) {
            inOurLeaf = false;
            endNodeWithDisabledValidation();
        } else {
            super.endNode();
        }
    }

    /**
     * Writing of the end element with disabled validation.
     *
     * @throws IOException Writing of the end element to the output stream failed.
     */
    abstract void endNodeWithDisabledValidation() throws IOException;
}