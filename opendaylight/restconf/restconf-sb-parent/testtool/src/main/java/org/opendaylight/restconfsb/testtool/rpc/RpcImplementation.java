/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.rpc;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.restconfsb.testtool.util.NormalizedNodeUtils;
import org.opendaylight.restconfsb.testtool.xml.rpc.Invocation;
import org.opendaylight.restconfsb.testtool.xml.rpc.Rpc;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class RpcImplementation {

    private final Rpc rpc;
    private final SchemaContext context;
    private final Map<NormalizedNode<?, ?>, String> invocations = new HashMap<>();
    private final RpcDefinition rpcDefinition;

    public RpcImplementation(final Rpc rpc, final SchemaContext context) {
        this.rpc = rpc;
        this.context = context;
        final Module moduleByName = context.findModuleByName(rpc.getModule(), null);
        final Set<RpcDefinition> rpcs = Preconditions.checkNotNull(moduleByName, "module not found " + rpc.getModule()).getRpcs();
        rpcDefinition = findRpcDefinition(rpcs);
        initInvocations();
    }

    private void initInvocations() {
        for (final Invocation invocation : rpc.getInvocations()) {
            final NormalizedNode<?, ?> input = NormalizedNodeUtils.parseRpcInput(context, rpcDefinition, invocation.getInput().getElement());
            invocations.put(input, XmlUtil.toString(invocation.getOutput().getElement()));
        }

    }

    public String getIdentifier() {
        return rpc.getModule() + ":" + rpc.getName();
    }

    public String invoke(final NormalizedNode<?, ?> input) {
        return invocations.get(input);
    }

    private RpcDefinition findRpcDefinition(final Set<RpcDefinition> rpcs) {
        for (final RpcDefinition rpcDef : rpcs) {
            final SchemaPath path = rpcDef.getPath();
            if (rpc.getName().equals(path.getLastComponent().getLocalName())) {
                return rpcDef;
            }
        }
        throw new IllegalStateException("Rpc schema path not found in context");
    }


}
