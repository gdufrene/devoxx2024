PASSWD=$(openssl rand 12 | base64 )
CSRF_TOKEN=$(curl -S --no-progress-meter http://localhost:7750/pulsar-manager/csrf-token)
curl -S --no-progress-meter \
    -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -H "Cookie: XSRF-TOKEN=$CSRF_TOKEN;" \
    -H 'Content-Type: application/json' \
    -X PUT http://localhost:7750/pulsar-manager/users/superuser \
    -d "{\"name\": \"admin\", \"password\": \"$PASSWD\", \"description\": \"test\", \"email\": \"username@test.org\"}"
    echo
cat <<eof
 Use http://localhost:9527 to access pulsar-manager.
 login is 'admin' and password is '$PASSWD'.
 register an environment with those parameters :
   [Environment Name]: any
   [Service URL]: http://pulsar:8080
   [Bookie URL]: http://pulsar:6650
eof
