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

public class YangLibRestApp extends Application {
    @Override
    public Set<Object> getSingletons() {
        return Collections.<Object>singleton(YangLibServiceImpl.getInstance());
    }
}
