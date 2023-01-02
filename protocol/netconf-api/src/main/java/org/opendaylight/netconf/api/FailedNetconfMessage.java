/*
 * Copyright (c) 2017 Bell Canada. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api;

/**
 * FailedNetconfMessage represents a wrapper around NetconfMessage.
 */
public class FailedNetconfMessage extends NetconfMessage {

    private Throwable exception;

    public FailedNetconfMessage(final Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
        return this.exception;
    }
}
