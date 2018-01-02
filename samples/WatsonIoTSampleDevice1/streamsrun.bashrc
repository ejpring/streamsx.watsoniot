#!/bin/bash

# Copyright (C) 2017  International Business Machines Corporation
# All Rights Reserved

##### load global definitions #####

[ -f /etc/bashrc ] && source /etc/bashrc

##### set process limits for InfoSphere Streams #####

ulimit -Su 10000
ulimit -Hu 10000

##### set InfoSphere Streams environment variables #####

[[ -d /opt/ibm/InfoSphere_Streams ]] && export STREAMS_INSTALL=/opt/ibm/InfoSphere_Streams/$( ls /opt/ibm/InfoSphere_Streams|grep -v var )

##### set Java environment variables #####

[[ -d $STREAMS_INSTALL/java ]] && export JAVA_HOME=$STREAMS_INSTALL/java
[[ -d $JAVA_HOME/jre/bin ]] && export PATH=$JAVA_HOME/jre/bin:$PATH

##### set Linux environment variables #####

export LANG=en_US.UTF-8
export EDITOR=emacs
export PATH=.:$HOME/bin:$PATH
export TZ=America/New_York
export LD_LIBRARY_PATH=$STREAMS_INSTALL/lib:$STREAMS_INSTALL/ext/lib/:$STREAMS_INSTALL/system/impl/lib/

