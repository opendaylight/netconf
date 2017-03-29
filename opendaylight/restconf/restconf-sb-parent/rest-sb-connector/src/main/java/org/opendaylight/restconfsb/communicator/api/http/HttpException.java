/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.http;

/**
 * General HTTP exception.
 */
public class HttpException extends RuntimeException {

    private final int status;
    private final String msg;

    public HttpException(final int status, final String msg) {
        super(String.format("%d: %s", status, msg));
        this.status = status;
        this.msg = msg;
    }

    public int getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }
}
