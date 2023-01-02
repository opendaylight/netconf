#!/bin/bash
#
# Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html

# Simple script to check consistency of the shaded artifact. It is by no means perfect,
# but it's better than nothing.
#
# Quick usage:
#
# nite@nitebug : ~/netconf/netconf/shaded-exificient-jar $ mvn clean install
# nite@nitebug : ~/netconf/netconf/shaded-exificient-jar $ mkdir x
# nite@nitebug : ~/netconf/netconf/shaded-exificient-jar $ cd x
# nite@nitebug : ~/netconf/netconf/shaded-exificient-jar/x $ rm -rf * && unzip ../target/shaded-exificient-jar-*-SNAPSHOT.jar && ../check_jar.sh .

root="$1"
if [ ! -d "$1" ]; then
    echo "Directory $root does not exist"
    exit 1
fi

if [ ! -f check_strings1.txt ]; then
    echo "Looking for strings ..."
    find "$root" -name \*.class | xargs strings | sort -u | fgrep shaded/ | sed 's/;/ /g' | sed "s/'//g"> check_strings1.txt
    rm -f check_strings2.txt
fi

if [ ! -f check_strings2.txt ]; then
    echo "Splitting strings ..."
    sort -u check_strings1.txt | xargs -n1 | fgrep shaded/ | sort -u > check_strings2.txt
    rm -f check_strings3.txt
fi

if [ ! -f check_strings3.txt ]; then
    echo "Cleaning strings ..."
    sed 's#.*shaded/##' check_strings2.txt | sort -u > check_strings3.txt
fi

for i in `cat check_strings3.txt`; do
    dir=`dirname "$i"`
    name=`basename "$i"`
    count=`find "$root/org/opendaylight/netconf/shaded/$dir" -name $name\* -printf . | wc -c`
    if [ $count -eq 0 ]; then
        echo ">>> Missing $i"
        echo "    References:"
        find "$root" -name \*.class | xargs fgrep -l "$i"
    fi
done

