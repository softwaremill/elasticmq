#!/bin/sh

# Based on http://mojo.codehaus.org/appassembler/appassembler-maven-plugin/ (Apache2 license)

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
 ls=`ls -ld "$PRG"`
 link=`expr "$ls" : '.*-> \(.*\)$'`
 if expr "$link" : '/.*' > /dev/null; then
   PRG="$link"
 else
   PRG=`dirname "$PRG"`/"$link"
 fi
done

PRGDIR=`dirname "$PRG"`
BASEDIR=`cd "$PRGDIR/.." >/dev/null; pwd`

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "`uname`" in
 CYGWIN*) cygwin=true ;;
 Darwin*) darwin=true
          if [ -z "$JAVA_VERSION" ] ; then
            JAVA_VERSION="CurrentJDK"
          else
            echo "Using Java version: $JAVA_VERSION"
          fi
          if [ -z "$JAVA_HOME" ] ; then
            JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/${JAVA_VERSION}/Home
          fi
          ;;
esac

if [ -z "$JAVA_HOME" ] ; then
 if [ -r /etc/gentoo-release ] ; then
   JAVA_HOME=`java-config --jre-home`
 fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
 [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
 [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# If a specific java binary isn't specified search for the standard 'java' binary
if [ -z "$JAVACMD" ] ; then
 if [ -n "$JAVA_HOME"  ] ; then
   if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
     # IBM's JDK on AIX uses strange locations for the executables
     JAVACMD="$JAVA_HOME/jre/sh/java"
   else
     JAVACMD="$JAVA_HOME/bin/java"
   fi
 else
   JAVACMD=`which java`
 fi
fi

if [ ! -x "$JAVACMD" ] ; then
 echo "Error: JAVA_HOME is not defined correctly." 1>&2
 echo "  We cannot execute $JAVACMD" 1>&2
 exit 1
fi

if [ -z "$REPO" ]
then
 REPO="$BASEDIR"/lib
fi

CLASSPATH=$CLASSPATH_PREFIX:"$REPO"/*
EXTRA_JVM_ARGUMENTS="-server -Dlogback.configurationFile=$BASEDIR/conf/logback.xml"

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
 [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
 [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
 [ -n "$HOME" ] && HOME=`cygpath --path --windows "$HOME"`
 [ -n "$BASEDIR" ] && BASEDIR=`cygpath --path --windows "$BASEDIR"`
 [ -n "$REPO" ] && REPO=`cygpath --path --windows "$REPO"`
fi

exec "$JAVACMD" $JAVA_OPTS \
 $EXTRA_JVM_ARGUMENTS \
 -classpath "$CLASSPATH" \
 org.elasticmq.server.Main \
 "$@"