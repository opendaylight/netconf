/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

class FutureDataFactory<T> {

    protected T result;
    private boolean statusFail = false;

    void setResult(final T result) {
        this.result = result;
    }

    void setFailureStatus() {
        this.statusFail = true;
    }

    boolean getFailureStatus() {
        return statusFail;
    }

}
