/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.transactions;

import java.io.Serializable;

/**
 * Message sent from master back to the slave when submit fails, with the offending exception attached.
 */
public class SubmitFailedReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Throwable throwable;

    public SubmitFailedReply(final Throwable throwable) {
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
