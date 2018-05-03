/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.yanglib.api.YangLibService;

public class YangLibRestApp extends Application {
    private final YangLibService yangLibService;

    public YangLibRestApp(YangLibService yangLibService) {
        this.yangLibService = yangLibService;
    }

    @Override
    public Set<Object> getSingletons() {
        return Collections.<Object>singleton(this.yangLibService);
    }
}
