#!/bin/bash
# Test cases for the canary version sed regex
# Usage: bash test-sed-regex.sh

# Strips -canary.<hash>-SNAPSHOT (and optional leading hash-SNAPSHOT) and .pom suffix
# Handles: single canary, double canary, PR-number canary, .pom suffix
#TAG=$(echo "$RELEASE_VERSION" | grep -q 'canary' \
#  && echo "$RELEASE_VERSION" | sed -E 's/-canary\..*$//' | sed -E 's/-[0-9a-f]+-SNAPSHOT$//' | sed -E 's/-[0-9a-f]+-SNAPSHOT$//' | { read base; echo "$base-$(echo "$RELEASE_VERSION" | sed -E 's/.*-canary\.([0-9]+\.)?([0-9a-f]+-SNAPSHOT)(\.pom)?$/\2/')"; } \
#  || echo "$RELEASE_VERSION")
#BASE=$(echo "$RELEASE_VERSION" | sed -E 's/-canary\..*$//' | sed -E 's/-[0-9a-f]+-SNAPSHOT$//' | sed -E 's/-[0-9a-f]+-SNAPSHOT$//')
#TAG=$(echo "$RELEASE_VERSION" | grep -q 'canary' && echo "$BASE-$(echo "$RELEASE_VERSION" | sed -E 's/.*-canary\.([0-9]+\.)?([0-9a-f]+-SNAPSHOT)(\.pom)?$/\2/')" || echo "$RELEASE_VERSION")

REGEX='s/^(.*)-[^-]+-SNAPSHOT-canary\.([0-9a-f]+-SNAPSHOT)(\.pom)?$/\1-\2/;s/^(.*)-canary\.[0-9]+\.([0-9a-f]+-SNAPSHOT)(\.pom)?$/\1-\2/;s/^(.*)-canary\.([0-9a-f]+-SNAPSHOT)(\.pom)?$/\1-\2/'

REGEX_ALT="$REGEX"
REGEX_USER="$REGEX"

PASS=0
FAIL=0

assert_eq() {
    local input="$1"
    local expected="$2"
    local regex="$3"
    local label="$4"
    local actual
    actual=$(echo "$input" | sed -E "$regex")
    if [[ "$actual" == "$expected" ]]; then
        echo "PASS [$label]: '$input' -> '$actual'"
        ((PASS++))
    else
        echo "FAIL [$label]: '$input' -> '$actual' (expected '$expected')"
        ((FAIL++))
    fi
}

echo ""
echo "=== Main regex ==="
echo ""

# Test user-provided regex on complex POM filename
assert_eq "saa-deployment_2.13-0.0.226-ecdc18e-SNAPSHOT-canary.23f8a98-SNAPSHOT.pom" \
          "saa-deployment_2.13-0.0.226-23f8a98-SNAPSHOT" \
          "$REGEX_USER" "user-regex"

# Canary with hash only (no PR number)
assert_eq "0.0.222-canary.2714b81-SNAPSHOT" "0.0.222-2714b81-SNAPSHOT" "$REGEX" ".*"

# Canary with PR number and hash
assert_eq "0.1.0-canary.99.abc1234-SNAPSHOT" "0.1.0-abc1234-SNAPSHOT" "$REGEX" ".*"

# Double canary (corrupted version)
assert_eq "0.0.222-canary.2714b81-SNAPSHOT-canary.bb0f437-SNAPSHOT" \
          "0.0.222-bb0f437-SNAPSHOT" \
          "$REGEX" ".*"

# Normal release version (no change)
assert_eq "1.2.3" "1.2.3" "$REGEX" ".*"

# All-digit hash
assert_eq "0.0.222-canary.1234567-SNAPSHOT" "0.0.222-1234567-SNAPSHOT" "$REGEX" ".*"

# Test for POM filename with multiple dashes and hashes
assert_eq "saa-deployment_2.13-0.0.226-ecdc18e-SNAPSHOT-canary.23f8a98-SNAPSHOT.pom" \
          "saa-deployment_2.13-0.0.226-23f8a98-SNAPSHOT" \
          "$REGEX" ".*"

# Canary with longer hash
assert_eq "2.0.0-canary.42.abcdef1234-SNAPSHOT" "2.0.0-abcdef1234-SNAPSHOT" "$REGEX" ".*"

# Normal version with patch (no change)
assert_eq "0.0.1" "0.0.1" "$REGEX" ".*"

echo ""
echo "=== Alternative regex (same) ==="
echo ""

# Canary with hash only (no PR number) - should pass
assert_eq "0.0.222-canary.2714b81-SNAPSHOT" "0.0.222-2714b81-SNAPSHOT" "$REGEX_ALT" "[^.]*"

# Canary with PR number and hash
assert_eq "0.1.0-canary.99.abc1234-SNAPSHOT" "0.1.0-abc1234-SNAPSHOT" "$REGEX_ALT" "[^.]*"

# Double canary (corrupted version)
assert_eq "0.0.222-canary.2714b81-SNAPSHOT-canary.bb0f437-SNAPSHOT" \
          "0.0.222-bb0f437-SNAPSHOT" \
          "$REGEX_ALT" "[^.]*"

# Normal release version (no change) - should pass
assert_eq "1.2.3" "1.2.3" "$REGEX_ALT" "[^.]*"

# All-digit hash - should pass
assert_eq "0.0.222-canary.1234567-SNAPSHOT" "0.0.222-1234567-SNAPSHOT" "$REGEX_ALT" "[^.]*"

# Test for POM filename with multiple dashes and hashes
assert_eq "saa-deployment_2.13-0.0.226-ecdc18e-SNAPSHOT-canary.23f8a98-SNAPSHOT.pom" \
          "saa-deployment_2.13-0.0.226-23f8a98-SNAPSHOT" \
          "$REGEX_ALT" "[^.]*"

# Canary with longer hash
assert_eq "2.0.0-canary.42.abcdef1234-SNAPSHOT" "2.0.0-abcdef1234-SNAPSHOT" "$REGEX_ALT" "[^.]*"

# Normal version with patch (no change) - should pass
assert_eq "0.0.1" "0.0.1" "$REGEX_ALT" "[^.]*"

echo ""
echo "Results: $PASS passed, $FAIL failed"
exit $FAIL