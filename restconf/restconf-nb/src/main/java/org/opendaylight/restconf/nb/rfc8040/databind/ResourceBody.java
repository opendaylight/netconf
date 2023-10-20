/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.restconf.Data;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
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
     * @param inference An {@link Inference} the statement corresponding to the body
     * @throws RestconfDocumentedException if the body cannot be decoded or it does not match {@code path}
     */
    // TODO: pass down DatabindContext corresponding to inference
    @SuppressWarnings("checkstyle:illegalCatch")
    public @NonNull NormalizedNode toNormalizedNode(final @NonNull YangInstanceIdentifier path,
            final @NonNull Inference inference, final @NonNull SchemaNode schemaNode) {
        final var expected = path.isEmpty() ? DATA_NID : path.getLastPathArgument();
        final var holder = new NormalizationResultHolder();
        try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
            streamTo(acquireStream(), inference, expected, streamWriter);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        } catch (RestconfDocumentedException e) {
            throw e;
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

        validTopLevelNodeName(expected, data);
        validateListKeysEqualityInPayloadAndUri(schemaNode, path, data);

        return data;
    }

    abstract void streamTo(@NonNull InputStream inputStream, @NonNull Inference inference, @NonNull PathArgument name,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;

    /**
     * Valid top level node name.
     *
     * @param path path of node
     * @param data data
     */
    @VisibleForTesting
    static final void validTopLevelNodeName(final PathArgument expected, final NormalizedNode data) {
        final var payloadName = data.name();
        if (!payloadName.equals(expected)) {
            throw new RestconfDocumentedException(
                "Payload name (" + payloadName + ") is different from identifier name (" + expected + ")",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
    }

    /**
     * Validates whether keys in {@code payload} are equal to values of keys in
     * {@code iiWithData} for list schema node.
     *
     * @throws RestconfDocumentedException if key values or key count in payload and URI isn't equal
     */
    @VisibleForTesting
    static final void validateListKeysEqualityInPayloadAndUri(final SchemaNode schemaNode,
            final YangInstanceIdentifier path, final NormalizedNode data) {
        if (schemaNode instanceof ListSchemaNode listSchema
            && path.getLastPathArgument() instanceof NodeIdentifierWithPredicates nip
            && data instanceof MapEntryNode mapEntry) {
            isEqualUriAndPayloadKeyValues(nip.asMap(), mapEntry, listSchema.getKeyDefinition());
        }
    }

    private static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
            final List<QName> keyDefinitions) {
        final var mutableCopyUriKeyValues = new HashMap<>(uriKeyValues);
        for (var keyDefinition : keyDefinitions) {
            final var uriKeyValue = mutableCopyUriKeyValues.remove(keyDefinition);
            if (uriKeyValue == null) {
                throw new RestconfDocumentedException("Missing key " + keyDefinition + " in URI.",
                    ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
            }

            final var dataKeyValue = payload.name().getValue(keyDefinition);
            if (!uriKeyValue.equals(dataKeyValue)) {
                throw new RestconfDocumentedException("The value '" + uriKeyValue
                    + "' for key '" + keyDefinition.getLocalName()
                    + "' specified in the URI doesn't match the value '" + dataKeyValue
                    + "' specified in the message body. ", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }
}