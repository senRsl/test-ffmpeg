#!/bin/bash

for((;;)); do ./ffmpeg -re -i out1.flv -vcodec copy -acodec copy -f flv -flvflags no_duration_filesize -y rtmp://192.168.7.89/live/livestream; sleep 1;  done