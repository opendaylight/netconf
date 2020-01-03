/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Exception that can be thrown from NETCONF chunk aggregator. It includes already aggregated bytes left in internal
 * byte buffer.
 */
public final class NetconfChunkException extends NetconfDocumentedException {
    private static final String BUFFERED_BYTES_TAG = "buffered-bytes";

    private final String hexBufferedBytes;

    private NetconfChunkException(final String hexBufferedBytes, final String errorMessage,
                                  final Map<String, String> errorInfo) {
        super(errorMessage, ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.WARNING, errorInfo);
        this.hexBufferedBytes = hexBufferedBytes;
    }

    /**
     * Creation of new {@link NetconfChunkException} using buffered bytes and error message.
     *
     * @param bufferedBytes Bytes that have already been buffered before an error happened.
     * @param errorMessage  Cause of this exception.
     * @return Instance of {@link NetconfChunkException}.
     */
    public static NetconfChunkException create(final byte[] bufferedBytes, final String errorMessage) {
        final String hexBufferedBytes = new String(bufferedBytes, StandardCharsets.UTF_8);
        final Map<String, String> errorInfo = Collections.singletonMap(BUFFERED_BYTES_TAG, hexBufferedBytes);
        return new NetconfChunkException(hexBufferedBytes, errorMessage, errorInfo);
    }

    /**
     * Getting of all buffered bytes formatted in {@link StandardCharsets#UTF_8}.
     *
     * @return String representation of buffered bytes.
     */
    public String getBufferedBytes() {
        return hexBufferedBytes;
    }
}