#!/bin/bash

filename=tunnels.tgz

echo DISTRIBUTION FILENAME WILL BE $filename

git pull

v=`cat version.txt`;
echo CURRENT VERSION IS $v
let v=$v+1
echo NEXT VERSION WILL BE $v
echo $v > version.txt
echo `date --rfc-3339='date'` $1 > date.txt

git add date.txt version.txt
git commit -m "packing a distribution"
git push

# tar zcvpf $filename --dereference --exclude='.git' --exclude '*~' jars tunnel.sh tunnel.bat version.txt date.txt gawk.exe

tar cvpf tunnels.tar --dereference --exclude='.git' --exclude '*~' tunnel.sh tunnel.bat loop.sh version.txt date.txt system.txt

tar rvpf tunnels.tar --dereference --exclude='.git' --exclude '*~' -C target sivantoledo.iot-1.0-jar-with-dependencies.jar

rm -f tunnels.tar.gz tunnels.tgz
gzip tunnels.tar
mv tunnels.tar.gz tunnels.tgz

ls -l $filename

echo DONE

