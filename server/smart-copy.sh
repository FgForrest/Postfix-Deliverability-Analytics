#!/bin/bash

HTTP_AUTH=$2
REMOTE_USER=$2
REMOTE_HOST=$3

DISTRIBUTION_PATH="./target/lib_mail_agent.tar.gz"
BOUNCE_REGEX_LIST_PATH="./src/main/resources/bounce-regex-list.xml"
DESTINATION_PATH="/www/p_prj/prj_mail/"
DESTINATION_BOUNCE_PATH="/www/p_prj/p_fg/massmailupdate/htdoc/html/agent/"
DISTRIBUTION_NAME="lib_mail_agent"
RESULTING_PATH="$DESTINATION_PATH$DISTRIBUTION_NAME"
PID_REGEX="^[0-9]+$"
W_TIMEOUT=10;

function copyToLocalAndRun () {
    if [ ! -f "$DISTRIBUTION_PATH" ] || [ -z "$HTTP_AUTH" ]; then
        echo
        echo "    ****   Please provide http authentication for operating application   "
        echo
        echo "           smart-copy.sh local admin:1234   "
        echo
        exit
    fi

    if [ -d "$RESULTING_PATH" ]; then
        DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
        if [[ "$DPID" =~ $PID_REGEX ]]; then
            echo "Agent is running with PID=$DPID"
            AGSTATUS=`curl -u $HTTP_AUTH http://127.0.0.1:1523/agent-shutdown 2>/dev/null`
            echo "Application shut down with status:"
            echo "$AGSTATUS";
            echo "Waiting ${W_TIMEOUT}s before kill."
            sleep $W_TIMEOUT;
            DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
            if [[ "$DPID" =~ $PID_REGEX ]]; then
                echo "Trying to kill Mail Agent."
                kill -9 $DPID
            fi
        fi
      echo "removing destination directory";
      rm -r "$RESULTING_PATH" > /dev/null
    fi

    if [ $? == 0 ]; then
        echo "gunzipping distribution to destination $DESTINATION_PATH";
        tar -C "$DESTINATION_PATH" -zxf "$DISTRIBUTION_PATH" > /dev/null
        if [ $? == 0 ]; then
            cd "${DESTINATION_PATH}${DISTRIBUTION_NAME}/bin"
            echo "Starting application"
            /bin/bash ./mailagentctl start $HTTP_AUTH
        fi
    fi
}

function copyToRemote () {
    if [ ! -f "$DISTRIBUTION_PATH" ] || [ -z "$REMOTE_USER" ] || [ -z "$REMOTE_HOST" ]; then
        echo
        echo "    ****   Please provide remote user and host      "
        echo
        echo "           smart-copy.sh remote admin charon.example.com   "
        echo
        exit
    fi

    echo "secure copying distribution to $REMOTE_USER@$REMOTE_HOST:${DESTINATION_PATH}"
    scp "$DISTRIBUTION_PATH" "$REMOTE_USER@$REMOTE_HOST:${DESTINATION_PATH}"
}

function copyBounceToRemote () {
    if [ ! -f "$DISTRIBUTION_PATH" ] || [ -z "$REMOTE_USER" ] || [ -z "$REMOTE_HOST" ]; then
        echo
        echo "    ****   Please provide remote user and host      "
        echo
        echo "           smart-copy.sh remoteBounce admin charon.example.com   "
        echo
        exit
    fi

    echo "secure copying bounce regex list to $REMOTE_USER@$REMOTE_HOST:${DESTINATION_BOUNCE_PATH}"
    scp "$BOUNCE_REGEX_LIST_PATH" "$REMOTE_USER@$REMOTE_HOST:${DESTINATION_BOUNCE_PATH}"
}


case "$1" in
        remoteBounce)
                copyBounceToRemote;
                ;;
        local)
                copyToLocalAndRun;
                ;;
        remote)
                copyToRemote;
                ;;
        *)
                cat <<EOF
Usage: $0 (local|remove)
  local              gunzip and copy distribution to its target destination
  remote             copy distribution to its remote target destination
EOF
                exit 1
                ;;
esac