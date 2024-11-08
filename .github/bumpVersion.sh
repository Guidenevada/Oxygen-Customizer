#!/bin/bash

NEWVERCODE=$(($(cat app/build.gradle.kts | grep versionCode | tr -s ' ' | cut -d " " -f 4 | tr -d '\r')+1))
NEWVERNAME=${GITHUB_REF_NAME/v/}

sed -i 's/versionCode.*/versionCode = '$NEWVERCODE'/' app/build.gradle.kts
sed -i 's/versionName =.*/versionName = "'$NEWVERNAME'"/' app/build.gradle.kts

sed -i 's/"version":.*/"version": "'$NEWVERNAME'",/' latestStable.json
sed -i 's/"versionCode":.*/"versionCode": '$NEWVERCODE',/' latestStable.json
sed -i 's/"apkUrl":.*/"apkUrl": "https:\/\/github.com\/DHD2280\/Oxygen-Customizer\/releases\/download\/'$GITHUB_REF_NAME'\/OxygenCustomizer.apk"/' latestStable.json

# app changelog
echo "**$NEWVERNAME**  " > newChangeLog.md
cat .github/workflowsFiles/FutureChanageLog.md >> newChangeLog.md
echo "  " >> newChangeLog.md
cat StableChangelog.md >> newChangeLog.md
mv  newChangeLog.md StableChangelog.md

# release notes
echo "**Changelog:**  " > releaseNotes.md
cat .github/workflowsFiles/FutureChanageLog.md >> releaseNotes.md

# changelog message
echo "*$NEWVERNAME* released in stable channel  " > telegram.msg
echo "  " >> telegram.msg
echo "*Changelog:*  " >> telegram.msg
cat .github/workflowsFiles/FutureChanageLog.md >> telegram.msg
echo 'TMessage<<EOF' >> $GITHUB_ENV
cat telegram.msg >> $GITHUB_ENV
echo >> $GITHUB_ENV
echo 'EOF' >> $GITHUB_ENV