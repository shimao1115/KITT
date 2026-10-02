#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit
JAVA_EXE=${JAVA_HOME:+"$JAVA_HOME/bin/java"}

if [ -z "$JAVA_EXE" ]; then
    JAVA_EXE=java
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no java command could be found in your PATH." >&2
    exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_EXE" $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
