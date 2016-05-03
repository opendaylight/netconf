/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

/**
 * TX Provider exceptions.
 */
public class TxException extends RuntimeException {

    public TxException(final String s, final RuntimeException e) {
        super(s, e);
    }

    public TxException(final String s) {
        super(s);
    }

    /**
     * Generic per-node-tx initialization failure
     */
    public static class TxInitiatizationFailedException extends TxException {

        public TxInitiatizationFailedException(final String s, final RuntimeException e) {
            super(s, e);
        }

        public TxInitiatizationFailedException(final String s) {
            super(s);
        }
    }
}
