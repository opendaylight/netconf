/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.api;

import org.opendaylight.yanglib.impl.YangLibServiceImpl;
import org.osgi.framework.BundleContext;

/**
 * Interface for register YangLibRestApp service via {@link BundleContext}.
 */
public interface YangLibRestAppService {

    /**
     * Get {@link YangLibServiceImpl} via service.
     * @return YangLibServiceImpl
     */
    YangLibServiceImpl getYangLibService();
}
