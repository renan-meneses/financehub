#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
javac --release 21 -d "$build_dir" \
  "$root/backend/src/main/java/com/financehub/financialaccounts/domain/Money.java" \
  "$root/backend/src/main/java/com/financehub/creditcards/domain/InstallmentAllocator.java" \
  "$root/backend/src/main/java/com/financehub/creditcards/domain/BillingCalendar.java" \
  "$root/backend/src/main/java/com/financehub/identity/domain/IdentityPolicy.java" \
  "$root/scripts/FinancialRulesCheck.java" \
  "$root/scripts/IdentityPolicyCheck.java"
java -cp "$build_dir" FinancialRulesCheck
java -cp "$build_dir" IdentityPolicyCheck
