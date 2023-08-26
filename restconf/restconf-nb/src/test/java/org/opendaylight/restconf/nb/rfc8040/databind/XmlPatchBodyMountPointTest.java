/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import org.opendaylight.mdsal.dom.api.DOMMountPoint;

public class XmlPatchBodyMountPointTest extends XmlPatchBodyTest {
    @Override
    String mountPrefix() {
        return "instance-identifier-module:cont/yang-ext:mount/";
    }

    @Override
    DOMMountPoint mountPoint() {
        return mountPoint;
    }
}
