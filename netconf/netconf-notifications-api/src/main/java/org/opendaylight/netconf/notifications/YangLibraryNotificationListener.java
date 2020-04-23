/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.YangLibraryChange;

/**
 * Listener for "yang-library-change" notification defined in https://tools.ietf.org/html/rfc7895.
 * This listener uses generated classes from yang model defined in RFC7895.
 */
public interface YangLibraryNotificationListener {

    /**
     * Callback used to notify about a change in the set of modules and submodules supported by the server.
     */
    void onYangLibraryChange(YangLibraryChange yangLibraryChange);
}
