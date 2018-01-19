#!/bin/bash

# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

################### functions used in this script #############################

die() { echo ; echo -e "\033[1;31m$*\033[0m" >&2 ; exit 1 ; }
step() { echo ; echo -e "\033[1;34m$*\033[0m" ; }

################### parameters used in this script ##############################

here=$( cd ${0%/*} ; pwd )

dependencies=( 
    $STREAMS_INSTALL/lib/com.ibm.streams.operator.jar
    $( find $here/opt -name "*.jar" ) 
)

compilerOptions=(
    -Xdiags:verbose
    -Xlint:unchecked
)

sourceFiles=( $( find $here/impl/java/src -name "*.java" ) )

binDirectory=$here/impl/java/bin

libDirectory=$here/impl/java/lib

jarFile=$libDirectory/com.ibm.streamsx.watsoniot.device.jar

###############################################################################

# make sure the Java complier and Streams tooling are available

echo "Java compiler:"
which javac || die "sorry, could not find the Java compiler, $?"
echo "Streams tooling:"
which spl-make-toolkit || die "sorry, could not find Streams tooling, $?"

step "compiling Java source files ..."
classpath=$( IFS=$':' ; echo -e "${dependencies[*]}" )
[[ -d $binDirectory ]] || mkdir -p $binDirectory || die "sorry, could not create directory $binDirectory, $?"
javac ${compilerOptions[*]} ${sourceFiles[*]} -classpath $classpath -d $binDirectory || die "sorry, could not compile Java source files, $?"

step "packing classfiles into $jarFile ..."
[[ -d $libDirectory ]] || mkdir -p $libDirectory || die "sorry, could not create directory $libDirectory, $?"
jar cf $jarFile -C $binDirectory . || die "sorry, could not create $jarFile, $?"
rm -rf $binDirectory || die "sorry, could not delete $binDirectory, $?"

step "indexing Java toolkit ..."
spl-make-toolkit -i $here || die "sorry, could not index Streams toolkit, $?"

exit 0

