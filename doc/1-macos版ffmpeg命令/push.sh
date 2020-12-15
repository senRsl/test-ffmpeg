#!/bin/bash

for((;;)); do ./ffmpeg -re -i out.flv -vcodec copy -acodec copy -f flv -y rtmp://192.168.7.89/live/livestream; sleep 1;  done