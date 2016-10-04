/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.yanglib.api.YangLibRestAppService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FrameworkUtil.class)
public class YangLibRestAppTest {

    @Test
    public void testYangLibRestApp() {
        PowerMockito.mockStatic(FrameworkUtil.class);

        final BundleContext bundleContext = mock(BundleContext.class);
        final Bundle bundle = mock(Bundle.class);

        given(FrameworkUtil.getBundle(any())).willReturn(bundle);
        given(bundle.getBundleContext()).willReturn(bundleContext);

        final YangLibRestApp yangLibRestApp = new YangLibRestApp();
        final Set singleton = yangLibRestApp.getSingletons();

        assertTrue(singleton.contains(yangLibRestApp.getYangLibService()));

        then(bundleContext).should(times(1)).registerService(eq(YangLibRestAppService.class.getName()), eq(yangLibRestApp), eq(null));
    }
}
