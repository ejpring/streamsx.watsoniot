#!/bin/bash

## Copyright (C) 2017  International Business Machines Corporation
## All Rights Reserved

################### parameters used in this script ##############################

#set -o xtrace
#set -o pipefail

here=$( cd ${0%/*} ; pwd )

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################################################################################

toolkitName=com.ibm.streamsx.watsoniot

toolkitDirectories=( $( find $here -name 'info.xml' -exec dirname {} \; ) )

toolkitPath=$( IFS=":" ; echo "${toolkitDirectories[*]}" )

spldocDirectory=$here/doc/spldoc

spldocOptions=(
    --toolkit-path $toolkitPath 
    --output-directory $spldocDirectory
    --check-tags 
    --include-all
    --copy-image-files
)

step "generating SPLDOC for toolkit $toolkitName ..."
spl-make-doc ${spldocOptions[*]} || die "sorry, could not make SPL documentation"

exit 0
