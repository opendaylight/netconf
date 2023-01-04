/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Application;
import org.opendaylight.yanglib.api.YangLibService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(service = {Application.class})
public class YangLibRestApp extends Application {
    private final YangLibService yangLibService;

    @Activate
    @Inject
    public YangLibRestApp(@Reference final YangLibService yangLibService) {
        this.yangLibService = requireNonNull(yangLibService);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(yangLibService);
    }
}
