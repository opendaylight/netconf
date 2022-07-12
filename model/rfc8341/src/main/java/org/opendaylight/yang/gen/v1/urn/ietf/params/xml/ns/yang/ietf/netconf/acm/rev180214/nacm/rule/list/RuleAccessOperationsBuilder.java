/*
 * Copyright (c) 2019 PATHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.MatchallStringType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list.Rule.AccessOperations;

public class RuleAccessOperationsBuilder {
    private static final AccessOperations MATCH_ALL = new AccessOperations(new MatchallStringType("*"));

    private RuleAccessOperationsBuilder() {
        //Exists only to defeat instantiation.
    }

    public static AccessOperations getDefaultInstance(final String defaultValue) {
        if ("*".equals(defaultValue)) {
            return MATCH_ALL;
        }
        // FIXME: parse bits
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
