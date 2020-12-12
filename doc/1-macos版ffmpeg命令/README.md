https://senrsl.blogspot.com/2020/12/android-rtmp01ffmpeg.html

# 推流给srs server

```shell
for((;;)); do \
    ./ffmpeg -re -i out.flv \
     -vcodec copy -acodec copy \
     -f flv -y rtmp://192.168.7.89/live/livestream; \
     sleep 1; \
 done
```