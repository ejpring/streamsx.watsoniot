#!/bin/bash

# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################### parameters used in this script ##############################

here=$( cd ${0%/*} ; pwd )

###############################################################################

stuff=(
    $here/.apt_generated 
    $here/impl/java/bin 
    $here/impl/java/src-gen 
    $here/lib 
    $here/output 
    $here/toolkit.xml 
    $here/.toolkitList
)

rm -rf ${stuff[*]}
exit $?



