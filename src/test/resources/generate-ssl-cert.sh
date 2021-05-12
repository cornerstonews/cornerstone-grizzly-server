#!/bin/bash

DIR="test-cert"

mkdir -p $DIR
mkdir -p $DIR
openssl genrsa -out $DIR/CA.key 2048
openssl req -x509 -new -nodes -sha256 -days 3650 -key $DIR/CA.key -out $DIR/CA.crt -subj "/C=UA/ST=Test/L=Test/O=Testy/OU=experiments/CN=test.lan"
openssl genpkey -algorithm RSA -out $DIR/site.key -outform PEM -pkeyopt rsa_keygen_bits:2048
openssl req -new -key $DIR/site.key -out $DIR/site.csr -subj "/C=UA/ST=Test/L=Test/O=Testy/OU=experiments/CN=localhost"
openssl x509 -req -in $DIR/site.csr -CA $DIR/CA.crt -CAkey $DIR/CA.key -CAcreateserial -out $DIR/site.crt -days 365 -sha256 -extfile site.ext
