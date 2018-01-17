#!/bin/bash

# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################### parameters used in this script ##############################

here=$( cd ${0%/*} ; pwd )

jars=( 
    $STREAMS_INSTALL/lib/com.ibm.streams.operator.jar
    $STREAMS_INSTALL/lib/com.ibm.streams.operator.samples.jar
    $( find $here/opt -name "*.jar" ) 
)

compilerOptions=(
    -Xdiags:verbose
    -Xlint:unchecked
)

sources=( $( find $here/impl/java/src -name "*.java" ) )

classDirectory="$here/impl/java/bin"

###############################################################################

# make sure the Java complier and Streams tooling are available

echo "Java compiler:"
which javac || die "sorry, could not find the Java compiler, $?"
echo "Streams tooling:"
which spl-make-toolkit || die "sorry, could not find Streams tooling, $?"

step "compiling Java source files ..."
classpath=$( IFS=$':' ; echo -e "${jars[*]}" )
[[ -d $classDirectory ]] || mkdir -p $classDirectory || die "sorry, could not create directory $classDirectory, $?"
javac ${compilerOptions[*]} ${sources[*]} -classpath $classpath -d $classDirectory || die "sorry, could not compile Java source files, $?"

step "indexing Java toolkit ..."
spl-make-toolkit -i $here || die "sorry, could not index Streams toolkit, $?"

exit 0

