#!/usr/bin/env sh

mkdir lib
wget https://github.com/Ichoran/thyme/raw/master/Thyme.jar -O lib/Thyme.jar

#cassVersion=""
#if java -version 2>&1 | egrep "java version \"1.7" > /dev/null; then
#    cassVersion="22x"
#else
#    cassVersion="31x"
#fi
#echo "deb http://www.apache.org/dist/cassandra/debian $cassVersion main" | sudo tee -a /etc/apt/sources.list.d/cassandra.sources.list
#echo "deb-src http://www.apache.org/dist/cassandra/debian $cassVersion main" | sudo tee -a /etc/apt/sources.list.d/cassandra.sources.list
#
#gpg --keyserver hkp://pgp.mit.edu:80 --recv-keys F758CE318D77295D
#gpg --export --armor F758CE318D77295D | sudo apt-key add -
#
#gpg --keyserver hkp://pgp.mit.edu:80 --recv-keys 2B5C1B00
#gpg --export --armor 2B5C1B00 | sudo apt-key add -
#
#gpg --keyserver hkp://pgp.mit.edu:80 --recv-keys 0353B12C
#gpg --export --armor 0353B12C | sudo apt-key add -
#
#sudo apt-get update -qq
#sudo apt-get install cassandra