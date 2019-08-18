/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.context;

/**
 * Possible insert parameter values - specification of how a resource should be inserted within an "ordered-by user"
 * list (see RFC-8040, section 4.8.5 for more information).
 */
public enum InsertParameter {
    FIRST("first"),
    LAST("last"),
    BEFORE("before"),
    AFTER("after");

    private final String label;

    InsertParameter(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}