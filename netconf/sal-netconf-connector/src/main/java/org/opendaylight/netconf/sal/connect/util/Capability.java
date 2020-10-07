/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * This class is used for holding Capabilities of device which can be used as key in HashMap.
 */
public class Capability {

    private final List<String> nonModuleCap;
    private final List<String> moduleCap;
    private int hashCode = 0;

    public Capability(Set<String> nonModuleCap, Set<QName> moduleCap) {
        this.nonModuleCap = nonModuleCap.stream().sorted().collect(Collectors.toList());
        this.moduleCap = moduleCap.stream().map(qName -> qName.toString()).sorted().collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        } else {
            hashCode = Objects.hash(moduleCap, nonModuleCap);
            return hashCode;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (Objects.isNull(object)) {
            return false;
        } else if (this == object) {
            return true;
        } else if (!(object instanceof Capability)) {
            return false;
        }
        Capability capability = (Capability) object;
        if (Objects.equals(this.moduleCap, capability.moduleCap) && Objects.equals(this.nonModuleCap,
                capability.nonModuleCap)) {
            return true;
        }
        return false;
    }
}
