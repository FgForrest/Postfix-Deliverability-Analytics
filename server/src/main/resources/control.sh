#!/bin/bash

CURL_BASE="curl http://127.0.0.1:1523"
CURL_BASE_HEADERS="curl -D - http://127.0.0.1:1523"
W_TIMEOUT=10;
PID_REGEX="^[0-9]+$"
START_SCRIPT="./start.sh"

if [ -z "$JAVA_HOME" ] ; then
    export JAVA_HOME="/www/server/java/jdk"
fi

if [[ -n "$1" ]] && [[ "$1" != "start" ]] && [[ -z "$2" ]] && [[ `curl -sI http://127.0.0.1:1523 | tr -d '\r' | sed -En 's/^HTTP\/1\.1 (.*)/\1/p'` == "401 Unauthorized" ]]
then
   echo "    !!! Http server requires authentication, provide 'username:password' as last script argument !!!    "
   exit
fi

if [ -n "$2" ]
then
    CURL_BASE="curl -u $2 http://127.0.0.1:1523"
fi


function startDaemon () {
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  if [ -z "$DPID" ]; then
    /bin/bash $START_SCRIPT </dev/null >/dev/null 2>&1 &
    echo "Application started.";
  else
    echo "Application is already running, you might want to restart it";
  fi
}

function getStatus () {
  STATUS=0;
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  AGSTATUS=`${CURL_BASE}/agent-status 2>/dev/null`
  if [ $? == 0 ]; then
    echo "Application running with PID=$DPID and status:"
    echo "$AGSTATUS";
  elif [ "$DPID" != "" ]; then
    echo "Application running with PID=$DPID but not responding to status command."
  else
    echo "Application is not running."
  fi

  return $STATUS;
}

function getUnknownBounces () {
  STATUS=0;
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  AGSTATUS=`${CURL_BASE}/agent-status/unknown-bounces 2>/dev/null`
  if [ $? == 0 ]; then
    echo "Application running with PID=$DPID and status:"
    echo "$AGSTATUS";
  elif [ "$DPID" != "" ]; then
    echo "Application running with PID=$DPID but not responding to getUnknownBounces command."
  else
    echo "Application is not running."
  fi

  return $STATUS;
}

function restart () {
  STATUS=0;
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  if [ $? == 0 ] && [[ "$DPID" =~ $PID_REGEX ]]; then
    AGSTATUS=`${CURL_BASE}/agent-restart 2>/dev/null`
    echo "Application is being restarted with PID=$DPID and status:"
    echo "$AGSTATUS";
  elif [ -z "$DPID" ]; then
    echo "Application is not running. Starting now..."
    startDaemon;
  else
    echo "Please fix restart function of control.sh script, it does not work as expected"
  fi

  return $STATUS;
}

function reindex () {
  STATUS=0;
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  AGSTATUS=`${CURL_BASE}/agent-reindex 2>/dev/null`
  if [ $? == 0 ]; then
    echo "Application running with PID=$DPID and status:"
    echo "$AGSTATUS";
  elif [ "$DPID" != "" ]; then
    echo "Application running with PID=$DPID but not responding to reindex command."
  else
    echo "Application is not running."
  fi

  return $STATUS;
}

function refreshBouncelist () {
  STATUS=0;
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  AGSTATUS=`${CURL_BASE}/agent-refresh-bouncelist 2>/dev/null`
  if [ $? == 0 ]; then
    echo "Application running with PID=$DPID and status:"
    echo "$AGSTATUS";
  elif [ "$DPID" != "" ]; then
    echo "Application running with PID=$DPID but not responding to refreshBouncelist command."
  else
    echo "Application is not running."
  fi

  return $STATUS;
}

function stopDaemon () {
  AGSTOP=`${CURL_BASE}/agent-shutdown 2>/dev/null`
  if [ $? == 0 ]; then
    echo "Trying to stop Application with agent-shutdown command."
    echo "Waiting ${W_TIMEOUT}s before kill."
    sleep $W_TIMEOUT;
  fi
  DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
  if [ "$DPID" != "" ]; then
    echo "Trying to kill app."
    kill -9 $DPID
  fi
  echo "Application  stopped."
}

case "$1" in
        start)
                startDaemon;
                ;;
        stop)
                stopDaemon;
                ;;
        restart)
                restart;
                ;;
        reindex)
                reindex;
                ;;
        refreshBouncelist)
                refreshBouncelist;
                ;;
        unknownBounces)
                getUnknownBounces;
                ;;
        status)
                getStatus; STATUS_RC=$?;
                exit $STATUS_RC;
                ;;
        *)
                cat <<EOF
Usage: $0 (start|stop|restart|reindex|refreshBouncelist|unknownBounces|status) [user:password]
  start              Start app
  stop               Stop app
  restart            Restart app (for restarting http server, reloading configuration, refreshing bounce list and reindexing)
  reindex            Reindex logs (for refreshing bounce list and reindexing)
  refreshBouncelist  Refreshes list of regular expressions for bounce categorization
  unknownBounces     Show all unknown bounce log entries (to add corresponding regular expression to bounce list)
  status             Show app status
EOF
                exit 1
                ;;
esac