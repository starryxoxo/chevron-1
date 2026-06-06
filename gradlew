#!/usr/bin/env sh

# ---------------------------------------------------------------------------
# Gradle start up script for POSIX systems
# ---------------------------------------------------------------------------

# Resolve links - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done

SAVED="`pwd`"
cd "`dirname \"$PRG\"`" >/dev/null
APP_HOVER="`pwd`"
cd "$SAVED" >/dev/null

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Locate the wrapper jar file
CLASSPATH=$APP_HOVER/gradle/wrapper/gradle-wrapper.jar

# Execute Gradle engine directly inside Java Virtual Machine
exec "$JAVACMD" "-Xmx64m" "-Xms64m" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
