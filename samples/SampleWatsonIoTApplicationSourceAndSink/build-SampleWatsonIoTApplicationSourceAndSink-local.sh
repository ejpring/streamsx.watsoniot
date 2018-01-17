#!/bin/bash

# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################### parameters used in this script ##############################

here=$( cd ${0%/*} ; pwd )

toolkitDirectory=$HOME/StreamsToolkits

applicationDirectory=$here

applicationNamespace=com.ibm.streamsx.watsoniot.sample.application

applicationComposite=SampleWatsonIoTApplicationSourceAndSink

applicationCompileTimeParameterList=(
)

streamsToolkitList=(
    $toolkitDirectory/streamsx.json.toolkits-1.4.3_20171106/com.ibm.streamsx.json
    $here/../../com.ibm.streamsx.watsoniot
)

streamsCompilerOptionsList=(
    --verbose-mode
    --spl-path=$( IFS=: ; echo "${streamsToolkitList[*]}" )
    --optimized-code-generation
    --main-composite=$applicationNamespace::$applicationComposite
)

gccOptions=""

ldOptions=""

###############################################################################

# make sure the Streams complier is available

echo "Streams compiler:"
which sc || die "sorry, could not find the Streams compiler, $?"

step "rebuild streamsx.watsoniot toolkit ..."
$here/../../com.ibm.streamsx.watsoniot/build.sh || die "sorry, could not re-build streamsx.watsoniot toolkit, $?"

# make sure the Streams application source file exists

cd $applicationDirectory || die "sorry, could not change to directory $applicationDirectory, $?"
[[ -f ./$applicationNamespace/$applicationComposite.spl ]] || die "sorry, could not find Streams application source file $applicationDirectory/$applicationNamespace/$applicationComposite.spl, $?"

# log parameters used for this compilation

( IFS=$'\n' ; echo -e "\nStreams toolkits:\n${streamsToolkitList[*]}" )
( IFS=$'\n' ; echo -e "\nStreams compiler options:\n${streamsCompilerOptionsList[*]}" )
( IFS=$'\n' ; echo -e "\napplication compile-time parameters:\n${applicationCompileTimeParameterList[*]}" )
echo -e "\nGNU compiler parameters:\n$gccOptions" 
echo -e "\nGNU linker parameters:\n$ldOptions" 

# compile Streams application

step "compiling Streams application $applicationNamespace::$applicationComposite ..."
sc ${streamsCompilerOptionsList[*]} \"--cxx-flags=$gccOptions\" \"--ld-flags=$ldOptions\" ${applicationCompileTimeParameterList[*]} || die "Sorry, could not compile Streams application $applicationNamespace::$applicationComposite, $?" 

step "Done."
echo "built SAB bundle $here/output/$applicationNamespace.$applicationComposite.sab"
exit 0

