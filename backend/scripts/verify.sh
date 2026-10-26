#!/usr/bin/env sh
set -eu
root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
workspace=$(dirname -- "$root")
for project in infrastructure/config-server infrastructure/discovery-server services/api-gateway services/user-service services/product-service services/order-service services/payment-service services/notification-service; do
  mvn -B -ntp "-Dmaven.repo.local=$workspace/.m2/repository" -f "$root/$project/pom.xml" verify
done
