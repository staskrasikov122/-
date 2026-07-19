#!/bin/sh

##############################################################################
#   Gradle start up script for POSIX
##############################################################################

app_path=$0
while
    APP_HOME=${app_path%"${app_path##*/}"}
    [ -h "$app_path" ]
do
    ls=$( ls -ld -- "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*)   app_path=$link ;;
      *)    app_path=$APP_HOME$link ;;
    esac
done

APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD=$JAVA_HOME/bin/java
    else
        echo "ERROR: JAVA_HOME invalid: $JAVA_HOME"
        exit 1
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1 ; then
        echo "ERROR: JAVA_HOME not set and java not found."
        exit 1
    fi
fi

exec "$JAVACMD" \
    -Xmx64m -Xms64m \
    -Dfile.encoding=UTF-8 \
    $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    "$@"
