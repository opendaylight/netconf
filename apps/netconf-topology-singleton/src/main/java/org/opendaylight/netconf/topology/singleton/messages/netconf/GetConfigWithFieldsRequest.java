/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages.netconf;

import com.google.common.collect.ImmutableList;
import java.io.Serial;
import java.util.List;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class GetConfigWithFieldsRequest extends GetConfigRequest {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<YangInstanceIdentifier> fields;

    public GetConfigWithFieldsRequest(final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        super(path);
        this.fields = ImmutableList.copyOf(fields);
    }

    public List<YangInstanceIdentifier> getFields() {
        return fields;
    }
}