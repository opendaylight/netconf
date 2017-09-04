/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl.jmx;

public class Operational {
    private Get get;

    public Get getGet() {
        return get;
    }

    public void setGet(Get get) {
        this.get = get;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(get);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Operational that = (Operational) obj;
        return java.util.Objects.equals(get, that.get);

    }
}
