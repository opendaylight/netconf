/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import akka.actor.ActorRef;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Master sends the message to slave with necessary parameters for creating slave mount point.
 */
public class RegisterMountPoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<SourceIdentifier> allSourceIdentifiers;
    private final ActorRef masterActorRef;

    public RegisterMountPoint(final List<SourceIdentifier> allSourceIdentifiers, ActorRef masterActorRef) {
        this.allSourceIdentifiers = allSourceIdentifiers;
        this.masterActorRef = masterActorRef;
    }

    public List<SourceIdentifier> getSourceIndentifiers() {
        return allSourceIdentifiers;
    }

    public ActorRef getMasterActorRef() {
        return masterActorRef;
    }

    @Override
    public String toString() {
        return "RegisterMountPoint [allSourceIdentifiers=" + allSourceIdentifiers + ", masterActorRef=" + masterActorRef
                + "]";
    }
}
