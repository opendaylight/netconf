/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.text.ParseException;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class for parsing identifier.
 */
public final class ParserIdentifier {
    private ParserIdentifier() {
        // Hidden on purpose
    }

    /**
     * Make {@link InstanceIdentifierContext} from {@link String} identifier.
     * <br>
     * For identifiers of data NOT behind mount points returned
     * {@link InstanceIdentifierContext} is prepared with {@code null} reference of {@link DOMMountPoint} and with
     * controller's {@link SchemaContext}.
     * <br>
     * For identifiers of data behind mount points returned
     * {@link InstanceIdentifierContext} is prepared with reference of {@link DOMMountPoint} and its
     * own {@link SchemaContext}.
     *
     * @param identifier path identifier
     * @param schemaContext controller schema context
     * @param mountPointService mount point service
     * @return {@link InstanceIdentifierContext}
     */
    public static InstanceIdentifierContext toInstanceIdentifier(final String identifier,
            final EffectiveModelContext schemaContext, final @Nullable DOMMountPointService mountPointService) {
        final ApiPath apiPath;
        try {
            apiPath = ApiPath.parseUrl(identifier);
        } catch (ParseException e) {
            throw new RestconfDocumentedException(e.getMessage(), ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }

        return InstanceIdentifierContext.ofApiPath(apiPath, schemaContext, mountPointService);
    }
}
