#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import os
import string
from typing import List

import requests

from libraries import utils
from libraries.variables import variables

ODL_IP = variables.ODL_IP
RESTCONF_PORT = variables.RESTCONF_PORT
MAX_HTTP_RESPONSE_BODY_LOG_SIZE = variables.MAX_HTTP_RESPONSE_BODY_LOG_SIZE
BASE_URL = f"http://{ODL_IP}:{RESTCONF_PORT}"

ALLOWED_STATUS_CODES = {200, 201, 204}
ALLOWED_DELETE_STATUS_CODES = {200, 201, 204, 404, 409}
DELETED_STATUS_CODES = {404, 409}

log = logging.getLogger(__name__)


def get_from_uri(
    uri: str, headers: dict | None = None, expected_code: int | List[int] | None = None
) -> requests.Response:
    """Sends HTTP GET request to ODL.

    Args:
        url (str): URL address.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for GET call.
    """
    url = f"{BASE_URL}/{uri}"
    log.info(f"Sending GET request to {url}")
    if not headers:
        headers = {}
    response = requests.get(
        url, headers=headers, auth=requests.auth.HTTPBasicAuth("admin", "admin")
    )

    try:
        if expected_code is not None:
            if isinstance(expected_code, int):
                # convert single value to tuple of integers
                expected_code = (expected_code,)
            if response.status_code not in expected_code:
                raise AssertionError(
                    f"Unexpected resonse code {response.status_code}, expected "
                    f"{expected_code}."
                )
        else:
            response.raise_for_status()
    except (requests.HTTPError, AssertionError) as e:
        log.error(f"Response: {response.text}")
        log.error(f"Response code: {response.status_code}")
        log.error(f"Response headers: {response.headers}")
        raise AssertionError("Unexpected failure in GET response.") from e
    else:
        response_text = utils.truncate_long_text(response.text, MAX_HTTP_RESPONSE_BODY_LOG_SIZE)
        log.debug(f"Response: {response_text}")
        log.info(f"Response code: {response.status_code}")
        log.debug(f"Response headers: {response.headers}")

    return response


def put_to_uri_request(
    uri: str,
    headers: dict,
    data: dict | str,
    expected_code: int | List[int] | None = None,
) -> requests.Response:
    """Sends HTTP PUT request to ODL using provided data.

    Args:
        uri (str): URI identifier.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.
        data (dict | str): payload to be sent within the PUT request to ODL

    Returns:
        requests.Response: Response returned by ODL for PUT call.
    """
    url = f"{BASE_URL}/{uri}"
    log.info(f"Sending PUT request to {url} using this data: {data}")
    response = requests.put(
        url=url,
        data=data,
        headers=headers,
        auth=requests.auth.HTTPBasicAuth("admin", "admin"),
    )

    try:
        if expected_code is not None:
            if isinstance(expected_code, int):
                # convert single value to tuple of integers
                expected_code = (expected_code,)
            if response.status_code not in expected_code:
                raise AssertionError(
                    f"Unexpected resonse code {response.status_code}, expected "
                    f"{expected_code}."
                )
        else:
            response.raise_for_status()
    except (requests.HTTPError, AssertionError) as e:
        log.error(f"Response: {response.text}")
        log.error(f"Response code: {response.status_code}")
        log.error(f"Response headers: {response.headers}")
        raise AssertionError("Unexpected failure in PUT response.") from e
    else:
        response_text = utils.truncate_long_text(response.text, MAX_HTTP_RESPONSE_BODY_LOG_SIZE)
        log.debug(f"Response: {response_text}")
        log.info(f"Response code: {response.status_code}")
        log.debug(f"Response headers: {response.headers}")

    return response


def post_to_uri(
    uri: str,
    headers: dict,
    data: dict | str,
    expected_code: int | List[int] | None = None,
) -> requests.Response:
    """Send HTTP POST request to ODL.

    Args:
        uri (str): URI identifier.
        data (dict | str): payload to be sent within the POST request to ODL.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for PUT call.
    """
    url = f"{BASE_URL}/{uri}"
    log.info(f"Sending to {url} this data: {data}")
    response = requests.post(
        url=url,
        data=data,
        headers=headers,
        auth=requests.auth.HTTPBasicAuth("admin", "admin"),
    )

    try:
        if expected_code is not None:
            if isinstance(expected_code, int):
                # convert single value to tuple of integers
                expected_code = (expected_code,)
            if response.status_code not in expected_code:
                raise AssertionError(
                    f"Unexpected resonse code {response.status_code}, expected "
                    f"{expected_code}."
                )
        else:
            response.raise_for_status()
    except (requests.HTTPError, AssertionError) as e:
        log.error(f"Response: {response.text}")
        log.error(f"Response code: {response.status_code}")
        log.error(f"Response headers: {response.headers}")
        raise AssertionError("Unexpected failure in POST response.") from e
    else:
        response_text = utils.truncate_long_text(response.text, MAX_HTTP_RESPONSE_BODY_LOG_SIZE)
        log.debug(f"Response: {response_text}")
        log.info(f"Response code: {response.status_code}")
        log.debug(f"Response headers: {response.headers}")

    return response


def delete_from_uri_request(
    uri: str, expected_code: int | List[int] | None = None
) -> requests.Response:
    """Sends HTTP DELETE request to ODL.

    Args:
        url (str): URL address.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for GET call.
    """
    url = f"{BASE_URL}/{uri}"
    log.info(f"Sending DELETE request to {url}")
    response = requests.delete(
        url=url, auth=requests.auth.HTTPBasicAuth("admin", "admin")
    )

    try:
        if expected_code is not None:
            if isinstance(expected_code, int):
                # convert single value to tuple of integers
                expected_code = (expected_code,)
            if response.status_code not in expected_code:
                raise AssertionError(
                    f"Unexpected resonse code {response.status_code}, expected "
                    f"{expected_code}."
                )
        else:
            response.raise_for_status()
    except (requests.HTTPError, AssertionError) as e:
        log.error(f"Response: {response.text}")
        log.error(f"Response code: {response.status_code}")
        log.error(f"Response headers: {response.headers}")
        raise AssertionError("Unexpected failure in DELETE response.") from e
    else:
        response_text = utils.truncate_long_text(response.text, MAX_HTTP_RESPONSE_BODY_LOG_SIZE)
        log.debug(f"Response: {response_text}")
        log.info(f"Response code: {response.status_code}")
        log.debug(f"Response headers: {response.headers}")

    return response


def resolve_templated_text(template_location: str, mapping: dict) -> str:
    """Evaluates templated text using provided value mapping.

    Args:
        template_location (str): Path to template text file.
        mapping (dict): Dictionary with all value mapping between
            placeholder values specified in template and expected value.

    Returns:
        str: Evaluated template file.
    """
    with open(template_location) as template_file:
        template = template_file.read()
    resolved_tempate = string.Template(template.rstrip()).safe_substitute(mapping)

    return resolved_tempate


def get_templated_request(
    temlate_dir: str,
    mapping: dict,
    json: bool = True,
    verify: bool = False,
    expected_code: int | List[int] | None = None,
) -> requests.Response:
    """Evaluates and sends GET request using template file.

    It uses provided data mapping between placeholder marks and expected
    values.

    Args:
        temlate_dir (str): Path to directory containing template text file(s).
        mapping (dict): Dictionary with all value mapping between placeholder
            values specified in template and expected value.
        json (bool): If true, use json template (file name with .json suffix),
            otherwise use xml tempale (file anem with .xml suffix).
        verify (bool): If true, verify returned response with stored
            template file.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for GET call.
    """
    if json:
        headers = {"Accept": "application/yang-data+json"}
    else:
        headers = {"Accept": "application/yang-data+xml"}
    uri = resolve_templated_text(temlate_dir + "/location.uri", mapping)
    response = get_from_uri(uri, headers=headers, expected_code=expected_code)

    if verify:
        file_name_suffix = "json" if json else "xml"
        expected_response = resolve_templated_text(
            temlate_dir + "/data." + file_name_suffix, mapping
        )
        volatiles_list = resolve_volatiles_path(temlate_dir)
        try:
            utils.verify_jsons_match(
                response.text,
                expected_response,
                "received response",
                "expected response",
                volatiles_list,
            )
        except AssertionError as e:
            raise AssertionError(
                "Received response does not match expected response:"
            ) from e

    return response


def put_templated_request(
    temlate_dir: str,
    mapping: dict,
    json: bool = True,
    verify: bool = False,
    expected_code: int | List[int] | None = None,
) -> requests.Response:
    """Evaluates and sends PUT request using template file.

    It uses provided data mapping between placeholder marks and expected
    values.

    Args:
        temlate_dir (str): Path to directory containing template text file(s).
        mapping (dict): Dictionary with all value mapping between placeholder
            values specified in template and expected value.
        json (bool): If true, use json template (file name with .json suffix),
            otherwise use xml tempale (file anem with .xml suffix).
        verify (bool): If true, verify returned response with stored
            template file.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for PUT call.
    """
    if json:
        data_file_name = "data.json"
        headers = {"Content-Type": "application/yang-data+json"}
    else:
        data_file_name = "data.xml"
        headers = {"Accept": "application/xml", "Content-Type": "application/xml"}
    uri = resolve_templated_text(temlate_dir + "/location.uri", mapping)
    data = resolve_templated_text(temlate_dir + "/" + data_file_name, mapping)
    response = put_to_uri_request(
        uri,
        headers,
        data,
        expected_code=expected_code,
    )

    if verify:
        file_name_suffix = "json" if json else "xml"
        expected_response = resolve_templated_text(
            temlate_dir + "/data." + file_name_suffix, mapping
        )
        volatiles_list = resolve_volatiles_path(temlate_dir)
        try:
            utils.verify_jsons_match(
                response.text,
                expected_response,
                "received response",
                "expected response",
                volatiles_list,
            )
        except AssertionError as e:
            raise AssertionError(
                "Received response does not match expected response:"
            ) from e

    return response


def post_templated_request(
    temlate_dir: str,
    mapping: dict,
    json=True,
    verify: bool = False,
    expected_code: int | List[int] | None = None,
    accept=None,
) -> requests.Response:
    """Evaluates and sends POST request using template file.

    It uses provided data mapping between placeholder marks and expected
    values.

    Args:
        temlate_dir (str): Path to directory containing template text file(s).
        mapping (dict): Dictionary with all value mapping between placeholder
            values specified in template and expected value.
        json (bool): If true, use json template (file name with .json suffix),
            otherwise use xml tempale (file anem with .xml suffix).
        verify (bool): If true, verify returned response with stored
            template file.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.


    Returns:
        requests.Response: Response returned by ODL for PUT call.
    """
    if json:
        data_file_name = "post_data.json"
        headers = {"Content-Type": "application/yang-data+json"}
    else:
        data_file_name = "post_data.xml"
        headers = {"Accept": "application/xml", "Content-Type": "application/xml"}
    if accept:
        headers["Accept"] = accept
    uri = resolve_templated_text(temlate_dir + "/location.uri", mapping)
    data = resolve_templated_text(temlate_dir + "/" + data_file_name, mapping)
    response = post_to_uri(
        uri,
        headers,
        data,
        expected_code=expected_code,
    )

    if verify:
        file_name_suffix = "json" if json else "xml"
        expected_response = resolve_templated_text(
            temlate_dir + "/data." + file_name_suffix, mapping
        )
        volatiles_list = resolve_volatiles_path(temlate_dir)
        try:
            utils.verify_jsons_match(
                response.text,
                expected_response,
                "received response",
                "expected response",
                volatiles_list,
            )
        except AssertionError as e:
            raise AssertionError(
                "Received response does not match expected response:"
            ) from e

    return response


def delete_templated_request(
    temlate_dir: str, mapping: dict, expected_code: int | List[int] | None = None
) -> requests.Response:
    """Evaluates and sends DELETE request using template file.

    It uses provided data mapping between placeholder marks and expected
    values.

    Args:
        temlate_dir (str): Path to directory containing template text file(s).
        mapping (dict): Dictionary with all value mapping between placeholder
            values specified in template and expected value.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for DELETE call.
    """
    uri = resolve_templated_text(temlate_dir + "/location.uri", mapping)
    response = delete_from_uri_request(uri, expected_code=expected_code)

    return response


def resolve_volatiles_path(template_dir) -> List[str]:
    """Reads list of volatiles values.

    Reads Volatiles List from file {template_dir}/volatiles.list if the file
    exists and returns the Volatiles List. Empty string is returned otherwise.

    Args:
        temlate_dir (str): Path to directory containing volatiles list.

    Returns:
        List[str]: List of volatiles values.
    """
    volatiles_file_path = f"{template_dir}/volatiles.list"
    if os.path.isfile(volatiles_file_path):
        with open(volatiles_file_path) as template_file:
            volatiles = template_file.read()
        volatiles_list = volatiles.split("\n")
    else:
        volatiles_list = ()

    return volatiles_list

def get_jinja_templated_request(
    temlate_dir: str,
    mapping: dict,
    filters=None,
    json: bool = True,
    verify: bool = False,
    expected_code: int | List[int] | None = None,
) -> requests.Response:
    """Sends GET request and verifies response using jinja template file.

    Args:
        temlate_dir (str): Path to directory containing jinja template.
        mapping (dict): Dictionary with all value mapping between placeholder
            values specified in template and expected value.
        filters (dict): Filters for modifying variables within jinja templates.
        json (bool): If true, request response in json format, otherwise in xml format.
        verify (bool): If true, verify returned response with stored jinja
            template file.
        expected_code (int | List[int] | None): Expected response code(s)
            returned by ODL. It could be either single numeric value or
            list of numbers. If not provided requests standard logic for
            evaluating failure response code is used.

    Returns:
        requests.Response: Response returned by ODL for GET call.
    """
    if json:
        headers = {"Accept": "application/yang-data+json"}
    else:
        headers = {"Accept": "application/yang-data+xml"}
    uri = resolve_templated_text(temlate_dir + "/location.uri", mapping)
    response = get_from_uri(uri, headers=headers, expected_code=expected_code)

    if verify:
        expected_response = utils.render_jinja_template(
            template_path=f"{temlate_dir}/data.j2",
            mapping=mapping,
            filters=filters
        )
        volatiles_list = resolve_volatiles_path(temlate_dir)
        try:
            utils.verify_jsons_match(
                response.text,
                expected_response,
                "received response",
                "expected response",
                volatiles_list,
            )
        except AssertionError as e:
            raise AssertionError(
                "Received response does not match expected response:"
            ) from e

    return response
