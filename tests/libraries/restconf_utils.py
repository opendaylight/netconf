#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import json
import logging
from typing import List

from libraries import templated_requests


log = logging.getLogger(__name__)


def check_for_elements_at_uri(uri: str, elements: List[str], pretty_print_json: bool = False):
    """
    A GET is made at the supplied {URI} and every item in the list of {elements}
    is verified to exist in the response

    Args:
        uri (str): Uri location.
        elements (List[str]): List of elements which are expected to be present
            in response.
        pretty_print_json (bool): Log received message in pretty format.

    Returns:
        None
    """
    resp = templated_requests.get_from_uri(uri=uri,expected_code=None)
    if pretty_print_json:
        log.info(json.dumps(resp.json()))
    else:
       log.info(resp.text)
    assert resp.status_code == 200
    for i in elements:
        assert i in resp.text, f"Expected element: {i} was not found in the response: {resp.text}"
