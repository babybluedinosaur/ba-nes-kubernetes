#!/usr/bin/env bash

DEBUG=false

# Parse flags
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--debug)
            DEBUG=true
            shift
            ;;
        -t|--topology)
            TOPOLOGY_FILE="$2"
            shift 2
            ;;
        *)
            if [ -z "${COMMAND}" ]; then
                COMMAND="$1"
            else
                QUERY_STRING="$1"
            fi
            shift
            ;;
    esac
done

if [ -z "${TOPOLOGY_FILE}" ]; then
    echo "Usage: $0 -t <topology-file> [dump|start] [query-string]"
    exit 1
fi

# If TOPOLOGY_FILE is "-", read from stdin and save to a temp file
if [ "${TOPOLOGY_FILE}" = "-" ]; then
    TOPOLOGY_FILE=$(mktemp)
    cat > "${TOPOLOGY_FILE}"
    CLEANUP_TEMP=true
fi

cleanup() {
    if [ -n "${QUERY_ID}" ]; then
        nes-cli -t "${TOPOLOGY_FILE}" stop "${QUERY_ID}" 2>/dev/null
    fi
    if [ "${CLEANUP_TEMP}" = true ]; then
        rm -f "${TOPOLOGY_FILE}"
    fi
    exit
}

trap cleanup EXIT INT TERM

# Handle dump command
if [ "${COMMAND}" = "dump" ]; then
    if [ "${DEBUG}" = true ]; then
        nes-cli -d -t "${TOPOLOGY_FILE}" dump
    else
        nes-cli -t "${TOPOLOGY_FILE}" dump
    fi
    exit $?
fi

# Validate query first using dump
echo "Validating query with dump..."
DUMP_OUTPUT=$(nes-cli -d -t "${TOPOLOGY_FILE}" dump 2>&1)
DUMP_EXIT=$?
echo "${DUMP_OUTPUT}"

if [ $DUMP_EXIT -ne 0 ]; then
    echo "Query validation failed (dump), skipping start"
    sleep 5
    exit 1
fi

# Handle start command
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