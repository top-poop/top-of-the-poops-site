

sudo s3fs TOTP ~/totp-b2 -o passwd_file=/home/totp/.totp/b2-passwd -o url=https://s3.us-west-000.backblazeb2.com



rclone mount totp-stream:totp-stream ~/totp-stream --s3-no-check-bucket --vfs-cache-mode writes

rclone sync totp-stream:totp-stream local-garage:totp-garage \
--progress \
--transfers 64 \
--checkers 64 \
--fast-list \
--multi-thread-streams 0

