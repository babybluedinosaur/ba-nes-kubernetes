#!/usr/bin/env bash

TOPOLOGY_FILE="$1"
QUERY_STRING="$2"

if [ -z "${TOPOLOGY_FILE}" ]; then
    echo "Usage: $0 <topology-file> [query-string]"
    exit 1
fi

cleanup() {
    if [ -n "${QUERY_ID}" ]; then
        nes-cli -t "${TOPOLOGY_FILE}" stop "${QUERY_ID}" 2>/dev/null
    fi
    exit
}

trap cleanup EXIT INT TERM

if [ -n "${QUERY_STRING}" ]; then
    QUERY_ID=$(nes-cli -t "${TOPOLOGY_FILE}" start "${QUERY_STRING}" 2>/dev/null)
else
    QUERY_ID=$(nes-cli -t "${TOPOLOGY_FILE}" start 2>/dev/null)
fi

if [ $? -ne 0 ] || [ -z "${QUERY_ID}" ]; then
    echo "Failed to start query"
    exit 1
fi

echo "Started query with ID: ${QUERY_ID}"

sleep 1

while true; do
    STATUS=$(nes-cli -t "${TOPOLOGY_FILE}" status "${QUERY_ID}" 2>/dev/null)
    echo "${STATUS}"

    QUERY_STATUS=$(echo "${STATUS}" | jq -r '.[0].query_status // empty')

    if [[ "${QUERY_STATUS}" == "Failed" || "${QUERY_STATUS}" == "Stopped" ]]; then
        echo "Query finished with status: ${QUERY_STATUS}"
        break
    fi

    sleep 1
done