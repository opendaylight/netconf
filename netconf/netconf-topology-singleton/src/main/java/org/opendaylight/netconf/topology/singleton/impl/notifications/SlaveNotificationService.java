/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.notifications;

import akka.actor.ActorRef;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.topology.singleton.impl.utils.ListenerRegistrationHolder;
import org.opendaylight.netconf.topology.singleton.impl.utils.StateHolder;
import org.opendaylight.netconf.topology.singleton.messages.RegisterSlaveListenerRequest;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * This service is created by slave. The user can register arbitrary listener but the listener does not react on device
 * callbacks (slave has no connection with device). It sends message to master, which create real listeners at needed
 * schema paths. Therefore the callbacks must be invoked manually in message InvokeNotificationListener (message sended
 * by master) in slave actor. When registrations are holded by state holder, this registrations are invoked, due to
 * setting application back to previous state and let notification running continue.
 */
public class SlaveNotificationService extends NetconfDeviceNotificationService
        implements NetconfNotificationListenerRegistrar {

    private final ActorRef masterActorRef;
    private final ActorRef myRef;
    private final StateHolder stateHolder;

    public SlaveNotificationService(final ActorRef masterActorRef, final ActorRef myRef,
                                    final StateHolder stateHolder) {
        super();
        this.masterActorRef = masterActorRef;
        this.myRef = myRef;
        this.stateHolder = stateHolder;
        registerNotificationListeners(stateHolder.getListenerRegistrations());
        registerStreams(stateHolder.getNetconfDeviceRegisteredStreams());
    }

    @Override
    public synchronized <T extends DOMNotificationListener> ListenerRegistration<T> registerNotificationListener(
            @Nonnull final T listener, @Nonnull final Collection<SchemaPath> types) {
        // send registration of listener to master

        masterActorRef.tell(new RegisterSlaveListenerRequest(types), myRef);
        stateHolder.add(new ListenerRegistrationHolder(listener, types));
        return super.registerNotificationListener(listener, types);

    }

    @Override
    public void registerNotificationListeners(final List<ListenerRegistrationHolder> listeners) {
        listeners.forEach(listener -> registerNotificationListener(listener.getDomNotificationListener(),
                listener.getTypes()));

    }

    @Override
    public void registerStreams(final List<String> netconfDeviceRegisteredStreams) {
        /*
         *  TODO: Actually we have no information about streams due to testool does not support nc-notification model.
         *  TODO: On the other hands here should be invoked rpc create-subscription on all stream.
         */

    }

}
