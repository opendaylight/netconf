/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

/**
 * Interface for session handler that is responsible for sending of data over established session.
 */
public interface SessionHandlerInterface {

    /**
     * Identification of created session.
     */
    boolean isConnected();

    /**
     * Interface for sending String message through one of implementation.
     *
     * @param message Message data to be send.
     */
    void sendDataMessage(String data);

}
