/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.Writer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class OneOperation extends OperationsBody {
    private final QName rpc;

    OneOperation(final EffectiveModelContext modelContext, final QName rpc) {
        super(modelContext);
        this.rpc = requireNonNull(rpc);
    }

    @Override
    void formatToJSON(final Writer out) throws IOException {
        // https://www.rfc-editor.org/rfc/rfc8040#page-84:
        //
        //            In JSON, the YANG module name identifies the module:
        //
        //              { 'ietf-system:system-restart' : [null] }
        out.write("{ ");
        appendJSON(out, jsonPrefix(rpc.getModule()), rpc);
        out.write(" }");
    }

    @Override
    void formatToXML(final Writer out) throws IOException {
        // https://www.rfc-editor.org/rfc/rfc8040#page-84:
        //
        //            In XML, the YANG module namespace identifies the module:
        //
        //              <system-restart
        //                 xmlns='urn:ietf:params:xml:ns:yang:ietf-system'/>
        appendXML(out, rpc);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("rpc", rpc);
    }
}