/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Strings;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.WriterParameters;

public class QueryParametersParser {

    private enum UriParameters {
        PRETTY_PRINT("prettyPrint"),
        DEPTH("depth");

        private final String uriParameterName;

        UriParameters(final String uriParameterName) {
            this.uriParameterName = uriParameterName;
        }

        @Override
        public String toString() {
            return this.uriParameterName;
        }
    }

    public static WriterParameters parseWriterParameters(final UriInfo info) {
        return parseParams(info, false);
    }

    public static WriterParameters parseWriterParameters(final UriInfo uriInfo, final boolean tagged) {
        return parseParams(uriInfo, tagged);
    }

    private static WriterParameters parseParams(final UriInfo info, final boolean tagged) {
        final WriterParameters.WriterParametersBuilder wpBuilder = new WriterParameters.WriterParametersBuilder();
        wpBuilder.setTagged(tagged);
        if (info == null) {
            return wpBuilder.build();
        }

        String param = info.getQueryParameters(false).getFirst(UriParameters.DEPTH.toString());
        if (!Strings.isNullOrEmpty(param) && !"unbounded".equals(param)) {
            try {
                final int depth = Integer.valueOf(param);
                if (depth < 1) {
                    throw new RestconfDocumentedException(
                            new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                            "Invalid depth parameter: " + depth, null,
                            "The depth parameter must be an integer > 1 or \"unbounded\""));
                }
                wpBuilder.setDepth(depth);
            } catch (final NumberFormatException e) {
                throw new RestconfDocumentedException(new RestconfError(
                        RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                        "Invalid depth parameter: " + e.getMessage(), null,
                        "The depth parameter must be an integer > 1 or \"unbounded\""));
            }
        }
        param = info.getQueryParameters(false).getFirst(UriParameters.PRETTY_PRINT.toString());
        wpBuilder.setPrettyPrint("true".equals(param));
        return wpBuilder.build();
    }

}
