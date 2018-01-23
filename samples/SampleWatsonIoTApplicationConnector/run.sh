#!/bin/bash

## Copyright (C) 2017  International Business Machines Corporation
## All Rights Reserved

################### parameters used in this script ##############################

#set -o xtrace
#set -o pipefail

here=$( cd ${0%/*} ; pwd )

namespace=com.ibm.streamsx.watsoniot.sample.application
composite=SampleWatsonIoTApplicationConnector

credentialsFilename=$here/WatsonIoTSampleApplication1.credentials.properties

submitParameterList=( 
    deviceType=SampleDeviceType
    deviceIds=SampleDevice1,SampleDevice2
    applicationCredentials=$( cat $credentialsFilename | tr '\n' ',' | tr -d ' ' )
    commandInterval=10
    timeoutInterval=60
)

traceLevel=3 # ... 0 for off, 1 for error, 2 for warn, 3 for info, 4 for debug, 5 for trace

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################################################################################

step "runtime configuration for application '$namespace.$composite' ..."
( IFS=$'\n' ; echo -e "\nsubmission-time parameters:\n${submitParameterList[*]}" )
echo -e "\ntrace level: $traceLevel"

step "running application '$namespace.$composite' ..."
executable=$here/output/bin/$namespace.$composite
$executable -t $traceLevel ${submitParameterList[*]} || die "sorry, application '$composite' failed, $?"

exit 0


