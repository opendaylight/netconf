/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.AccessOperationsType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.MatchallStringType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.rule.list.Rule.AccessOperations;

public class RuleAccessOperationsBuilder {
    private static final AccessOperations MATCH_ALL = new AccessOperations(new MatchallStringType("*"));

    private RuleAccessOperationsBuilder() {
        //Exists only to defeat instantiation.
    }

    public static AccessOperations getDefaultInstance(String defaultValue) {
        return "*".equals(defaultValue) ? MATCH_ALL : new AccessOperations(AccessOperationsType.getDefaultInstance(defaultValue));
    }

}
