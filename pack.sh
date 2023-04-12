#!/bin/bash

filename=tunnels.tgz

#echo DISTRIBUTION FILENAME WILL BE $filename

echo GIT PULL

git pull

echo UPDATE DATE AND VERSION NUMBER AND COMMIT

v=`cat version.txt`;
echo CURRENT VERSION IS $v
let v=$v+1
echo NEXT VERSION WILL BE $v
echo $v > version.txt
echo `date --rfc-3339='date'` $1 > date.txt

git add date.txt version.txt
git commit -m "packing a distribution"
git push

echo PACKING TGZ

# tar zcvpf $filename --dereference --exclude='.git' --exclude '*~' jars tunnel.sh tunnel.bat version.txt date.txt gawk.exe

tar cvpf tunnels.tar --dereference --exclude='.git' --exclude '*~' tunnel.sh tunnel.bat loop.sh version.txt date.txt system.txt

tar rvpf tunnels.tar --dereference --exclude='.git' --exclude '*~' -C target sivantoledo.iot-1.0-jar-with-dependencies.jar

rm -f tunnels.tar.gz tunnels.tgz
gzip tunnels.tar
mv tunnels.tar.gz tunnels.tgz

echo CONTENTS OF TGZ FILE
tar ztf tunnels.tgz

echo PACKING ZIP

rm -f tunnels.zip
zip --junk-paths tunnels.zip tunnel.sh tunnel.bat loop.sh version.txt date.txt system.txt target/sivantoledo.iot-1.0-jar-with-dependencies.jar

echo CONTENTS OF ZIP FILE

unzip -l tunnels.zip

echo ARCHIVE FILES:

ls -l tunnels.*

echo DONE

