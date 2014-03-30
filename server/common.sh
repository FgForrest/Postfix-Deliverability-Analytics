#!/bin/bash
. ./common.properties

PID_REGEX="^[0-9]+$"

function removeDeploy () {
      echo "*** Removing deploy ***";
      rm -r "$deploy_path/$distribution_name" > /dev/null
}

function stop () {
    DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
    if [[ "$DPID" =~ $PID_REGEX ]]; then
        echo "Application is running with PID=$DPID"
        AGSTATUS=`curl -u ${http_auth} http://127.0.0.1:1523/agent-shutdown 2>/dev/null`
        echo "Application shut down with status:"
        echo "$AGSTATUS";
        echo "Waiting ${kill_timeout}s before kill."
        sleep $kill_timeout;
        DPID=`ps aux 2>/dev/null | grep "[c]om.fg.mail.smtp.Agent" 2>/dev/null | awk '{ print $2 }' 2>/dev/null`
        if [[ "$DPID" =~ $PID_REGEX ]]; then
            echo "Trying to kill Mail Agent."
            kill -9 $DPID
        fi
    else
        echo "Application is not running"
    fi
}

function start () {
    stop

    if [ ! -d "${deploy_path}/${distribution_name}/bin" ]; then
        echo
        echo "    ****   Cannot start application because ${deploy_path}/${distribution_name}/bin does not exist    "
        echo
        exit
    fi

    cd "${deploy_path}/${distribution_name}/bin"
    echo "*** Starting application ***";
    /bin/bash ./control.sh start ${http_auth}
    sleep 1
    cd ${deploy_log_path}
    tail -f $(ls -1t | head -1)

}

function copyToLocal () {
    if [ -d "$deploy_path/$distribution_name" ]; then
        stop
        removeDeploy
    fi

    if [ $? != 0 ]; then
        exit
    fi

    echo "*** Gunzipping distribution to destination $deploy_path ***";
    tar -C "$deploy_path" -zxf "$distribution_path" > /dev/null
}

function copyToLocalAndRun () {
    copyToLocal
    if [ $? == 0 ]; then
        start
    fi
}

function backupDB () {
    if [ "$(ls -A ${deploy_path}/db/* 2>/dev/null)" ]; then
        echo "*** backing up existing DB files ***";
        zip -r "/tmp/db_backup.zip" "${deploy_path}/db";
    else
        echo "${deploy_path}/db is empty. No backup."
    fi
}

function deleteDB () {
    echo "*** removing existing DB files from ${deploy_path}/db/ ***";
    rm -f ${deploy_path}/db/*
}

function copyToLocalDeleteDBAndRun () {
    copyToLocal
    deleteDB
    if [ $? == 0 ]; then
        start
    fi
}

function copyToLocalBackupDeleteDBAndRun () {
    copyToLocal
    backupDB
    deleteDB
    if [ $? == 0 ]; then
        start
    fi
}

function copyToRemote () {
    if [ ! -f "$distribution_path" ] || [ -z "$remote_user" ] || [ -z "$remote_host" ]; then
        echo
        echo "    ****   Please provide remote user and host and verify $distribution_path exists    "
        echo
        echo "           common.sh copyToRemote admin charon.example.com   "
        echo
        exit
    fi

    echo "*** Secure copying distribution to $remote_user@$remote_host:${deploy_path} ***"
    scp "$distribution_path" "$remote_user@$remote_host:${deploy_path}"
}

function copyBounceToRemote () {
    if [ ! -f "$distribution_path" ] || [ -z "$remote_user" ] || [ -z "$remote_host" ]; then
        echo
        echo "    ****   Please provide remote user and host and verify $distribution_path exists     "
        echo
        echo "           common.sh copyBounceToRemote admin charon.example.com   "
        echo
        exit
    fi

    echo "*** Secure copying bounce regex list to $remote_user@$remote_host:${deploy_conf_path} ***"
    scp "$bounce_regex_list_path" "$remote_user@$remote_host:${deploy_conf_path}"
}

case "$1" in
        copyToLocal)
                copyToLocal;
                ;;
        copyToLocalAndRun)
                copyToLocalAndRun;
                ;;
        copyToLocalDeleteDBAndRun)
                copyToLocalDeleteDBAndRun;
                ;;
        copyToLocalBackupDeleteDBAndRun)
                copyToLocalBackupDeleteDBAndRun;
                ;;
        copyToRemote)
                copyToRemote;
                ;;
        copyBounceToRemote)
                copyBounceToRemote;
                ;;
        removeDeploy)
                removeDeploy;
                ;;
        backupDB)
                backupDB;
                ;;
        deleteDB)
                deleteDB;
                ;;
        stop)
                stop;
                ;;
        start)
                start;
                ;;
        *)
                cat <<EOF
Usage: $0 (start|stop|copyToLocal|copyToLocalAndRun|copyToLocalDeleteDBAndRun|copyToLocalBackupDeleteDBAndRun|copyToRemote|copyBounceToRemote|removeDeploy|deleteDB|backupDB)
    *** set up properties in common.sh properties ***

  start                             start or restart application
  stop                              stop application
  copyToLocal                       gunzip and copy distribution to its target destination
  copyToLocalAndRun                 gunzip and copy distribution to its target destination and start application
  copyToLocalDeleteDBAndRun         gunzip and copy distribution to its target destination, delete database and start application
  copyToLocalBackupDeleteDBAndRun   gunzip and copy distribution to its target destination, backup, delete database and start application
  copyToRemote                      copy distribution to its remote target destination
  copyBounceToRemote                copy boulce-regex-list.xml to a remote server
  removeDeploy                      delete deployed distribution
  backupDB                          backup database files
  deleteDB                          delete database files

EOF
                exit 1
                ;;
esac