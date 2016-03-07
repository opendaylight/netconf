/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.api.services;

import java.math.BigInteger;

public interface RestconfStatisticsService {

    BigInteger getConfigGet();

    BigInteger getSuccessGetConfig();

    BigInteger getFailureGetConfig();

    BigInteger getConfigPost();

    BigInteger getSuccessPost();

    BigInteger getFailurePost();

    BigInteger getConfigPut();

    BigInteger getSuccessPut();

    BigInteger getFailurePut();

    BigInteger getConfigDelete();

    BigInteger getSuccessDelete();

    BigInteger getFailureDelete();

    BigInteger getOperationalGet();

    BigInteger getSuccessGetOperational();

    BigInteger getFailureGetOperational();

    BigInteger getRpc();
}
