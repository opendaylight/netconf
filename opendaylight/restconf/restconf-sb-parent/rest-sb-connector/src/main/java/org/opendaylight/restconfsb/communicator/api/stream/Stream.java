/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api.stream;

public class Stream {

    private final String name;
    private final String location;
    private final boolean replaySupport;

    public Stream(String name, String location, boolean replaySupport) {
        this.name = name;
        this.location = location;
        this.replaySupport = replaySupport;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public boolean isReplaySupport() {
        return replaySupport;
    }
}
