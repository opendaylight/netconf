/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Data;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The body of a resource identified in the request URL, i.e. a {@code PUT} or a plain {@code PATCH} request on RESTCONF
 * data service.
 */
public abstract sealed class ResourceBody extends RequestBody permits JsonResourceBody, XmlResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceBody.class);
    private static final NodeIdentifier DATA_NID = NodeIdentifier.create(Data.QNAME);

    ResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Acquire the {@link NormalizedNode} representation of this body.
     *
     * @param path A {@link DatabindPath.Data} corresponding to the body
     * @throws ServerException if the body cannot be decoded or it does not match {@code path}
     */
    public final @NonNull NormalizedNode toNormalizedNode(final DatabindPath.@NonNull Data path)
            throws ServerException {
        final var instance = path.instance();
        final var expectedName = instance.isEmpty() ? DATA_NID : instance.getLastPathArgument();
        final var holder = new NormalizationResultHolder();
        try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
            streamTo(path, expectedName, consume(), streamWriter);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw path.databind().newProtocolMalformedMessageServerException("Error parsing input", e);
        }

        final var parsedData = holder.getResult().data();
        final NormalizedNode data;
        if (parsedData instanceof MapNode map) {
            // TODO: This is a weird special case: a PUT target cannot specify the entire map, but the body parser
            //       always produces a single-entry map for entries. We need to undo that damage here.
            data = map.body().iterator().next();
        } else if (parsedData instanceof SystemLeafSetNode<?> leafSetNode) {
            // Applying the same parser workaround logic as for MapNode above.
            data = leafSetNode.body().iterator().next();
        } else {
            data = parsedData;
        }

        final var dataName = data.name();
        if (!dataName.equals(expectedName)) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                "Payload name (%s) is different from identifier name (%s)", dataName, expectedName);
        }

        return data;
    }

    @NonNullByDefault
    abstract void streamTo(DatabindPath.Data path, PathArgument name, InputStream inputStream,
        NormalizedNodeStreamWriter writer) throws ServerException;
}