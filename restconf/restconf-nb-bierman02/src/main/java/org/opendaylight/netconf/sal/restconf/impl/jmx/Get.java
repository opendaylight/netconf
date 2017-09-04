/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl.jmx;

import java.math.BigInteger;

public class Get {
    private BigInteger successfulResponses;

    private BigInteger receivedRequests;

    private BigInteger failedResponses;

    public BigInteger getSuccessfulResponses() {
        return successfulResponses;
    }

    public void setSuccessfulResponses(BigInteger successfulResponses) {
        this.successfulResponses = successfulResponses;
    }

    public BigInteger getReceivedRequests() {
        return receivedRequests;
    }

    public void setReceivedRequests(BigInteger receivedRequests) {
        this.receivedRequests = receivedRequests;
    }

    public BigInteger getFailedResponses() {
        return failedResponses;
    }

    public void setFailedResponses(BigInteger failedResponses) {
        this.failedResponses = failedResponses;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(successfulResponses, receivedRequests, failedResponses);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Get that = (Get) obj;
        if (!java.util.Objects.equals(successfulResponses, that.successfulResponses)) {
            return false;
        }

        if (!java.util.Objects.equals(receivedRequests, that.receivedRequests)) {
            return false;
        }

        return java.util.Objects.equals(failedResponses, that.failedResponses);

    }
}
