/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.DataPutPath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Data;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The body of a resource identified in the request URL, i.e. a {@code PUT} or a plain {@code PATCH} request on RESTCONF
 * data service.
 */
public abstract sealed class ResourceBody extends AbstractBody permits JsonResourceBody, XmlResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceBody.class);
    private static final NodeIdentifier DATA_NID = NodeIdentifier.create(Data.QNAME);

    ResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Acquire the {@link NormalizedNode} representation of this body.
     *
     * @param path A {@link YangInstanceIdentifier} corresponding to the body
     * @throws RestconfDocumentedException if the body cannot be decoded or it does not match {@code path}
     */
    @SuppressWarnings("checkstyle:illegalCatch")
    public final @NonNull NormalizedNode toNormalizedNode(final @NonNull DataPutPath path)
            throws RestconfDocumentedException {
        final var instance = path.instance();
        final var expectedName = instance.isEmpty() ? DATA_NID : instance.getLastPathArgument();
        final var holder = new NormalizationResultHolder();
        try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
            streamTo(path, expectedName, acquireStream(), streamWriter);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        } catch (RuntimeException e) {
            throwIfYangError(e);
            throw e;
        }

        final var parsedData = holder.getResult().data();
        final NormalizedNode data;
        if (parsedData instanceof MapNode map) {
            // TODO: This is a weird special case: a PUT target cannot specify the entire map, but the body parser
            //       always produces a single-entry map for entries. We need to undo that damage here.
            data = map.body().iterator().next();
        } else {
            data = parsedData;
        }

        final var dataName = data.name();
        if (!dataName.equals(expectedName)) {
            throw new RestconfDocumentedException(
                "Payload name (" + dataName + ") is different from identifier name (" + expectedName + ")",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        return data;
    }

    abstract void streamTo(@NonNull DataPutPath path, @NonNull PathArgument name, @NonNull InputStream inputStream,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;
}