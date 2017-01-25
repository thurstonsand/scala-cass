#!/usr/bin/env sh

mkdir lib
wget https://github.com/Ichoran/thyme/raw/master/Thyme.jar -O lib/Thyme.jar

#sudo rm /etc/init.d/cassandra /etc/security/limits.d/cassandra.conf
#cassVersion=""
#if java -version 2>&1 | egrep "java version \"1.7" > /dev/null; then
#    cassVersion="2.2.7"
#else
#    cassVersion="3.0.8"
#fi

#wget "http://www.us.apache.org/dist/cassandra/$cassVersion/apache-cassandra-$cassVersion-bin.tar.gz" && tar -xvzf "apache-cassandra-$cassVersion-bin.tar.gz"
#export PATH="apache-cassandra-$cassVersion/bin:$PATH"
