/*
 * Copyright (c) 2019 PATHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.GroupNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.MatchallStringType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.acm.rev180214.nacm.RuleList.Group;

public class RuleListGroupBuilder {
    private static final Group MATCH_ALL = new Group(new MatchallStringType("*"));

    private RuleListGroupBuilder() {
        //Exists only to defeat instantiation.
    }

    public static Group getDefaultInstance(final String defaultValue) {
        return "*".equals(defaultValue) ? MATCH_ALL : new Group(new GroupNameType(defaultValue));
    }
}
