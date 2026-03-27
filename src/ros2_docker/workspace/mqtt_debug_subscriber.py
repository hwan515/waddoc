#!/usr/bin/env python3

import argparse
import os
import ssl
import sys
import time

import paho.mqtt.client as mqtt


def reason_code_value(reason_code):
    value = getattr(reason_code, "value", reason_code)
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0 if str(reason_code).lower() == "success" else -1


def parse_args():
    parser = argparse.ArgumentParser(
        description="Debug subscriber for the ROS2 MQTT bridge."
    )
    parser.add_argument(
        "--host",
        default=os.getenv("MQTT_BROKER_HOST", "www.waddoc.site"),
        help="MQTT broker host",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.getenv("MQTT_BROKER_PORT", "443")),
        help="MQTT broker port",
    )
    parser.add_argument(
        "--path",
        default=os.getenv("MQTT_WS_PATH", "/mqtt"),
        help="MQTT websocket path",
    )
    parser.add_argument(
        "--topic",
        default="robot/#",
        help="Topic filter to subscribe to",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="Seconds to wait before exiting",
    )
    parser.add_argument(
        "--max-messages",
        type=int,
        default=0,
        help="Exit after this many messages. 0 means unlimited.",
    )
    return parser.parse_args()


def create_client(state):
    callback_api_version = getattr(mqtt, "CallbackAPIVersion", None)
    if callback_api_version is not None:
        client = mqtt.Client(callback_api_version.VERSION2, transport="websockets")

        def on_connect(client, userdata, flags, reason_code, properties):
            rc = reason_code_value(reason_code)
            print(f"connected rc={rc}")
            if rc == 0:
                client.subscribe(state["topic"], qos=1)
                print(f"subscribed topic={state['topic']}")
                state["connected"] = True
            else:
                state["error"] = f"connect failed rc={rc}"

        def on_disconnect(client, userdata, disconnect_flags, reason_code, properties):
            rc = reason_code_value(reason_code)
            if not state["done"]:
                print(f"disconnected rc={rc}")

        client.on_connect = on_connect
        client.on_disconnect = on_disconnect
    else:
        client = mqtt.Client(transport="websockets")

        def on_connect(client, userdata, flags, rc):
            print(f"connected rc={rc}")
            if rc == 0:
                client.subscribe(state["topic"], qos=1)
                print(f"subscribed topic={state['topic']}")
                state["connected"] = True
            else:
                state["error"] = f"connect failed rc={rc}"

        def on_disconnect(client, userdata, rc):
            if not state["done"]:
                print(f"disconnected rc={rc}")

        client.on_connect = on_connect
        client.on_disconnect = on_disconnect

    def on_message(client, userdata, msg):
        payload = msg.payload.decode("utf-8", errors="replace")
        state["message_count"] += 1
        print(f"[{state['message_count']}] {msg.topic} {payload}")
        if state["max_messages"] and state["message_count"] >= state["max_messages"]:
            state["done"] = True

    client.on_message = on_message
    client.ws_set_options(path=state["path"])
    client.tls_set(cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLS)
    return client


def main():
    args = parse_args()
    state = {
        "topic": args.topic,
        "path": args.path,
        "max_messages": max(args.max_messages, 0),
        "message_count": 0,
        "connected": False,
        "done": False,
        "error": None,
    }

    client = create_client(state)

    print(
        f"connecting host={args.host} port={args.port} path={args.path} topic={args.topic}"
    )

    try:
        client.connect(args.host, args.port, 60)
        client.loop_start()

        deadline = None if args.timeout <= 0 else time.monotonic() + args.timeout
        while not state["done"]:
            if state["error"]:
                print(state["error"], file=sys.stderr)
                return 1
            if deadline is not None and time.monotonic() >= deadline:
                print(
                    f"timeout after {args.timeout:.1f}s, received={state['message_count']}"
                )
                return 0 if state["message_count"] > 0 else 2
            time.sleep(0.1)
    except KeyboardInterrupt:
        print("interrupted")
        return 130
    finally:
        state["done"] = True
        try:
            client.loop_stop()
        finally:
            try:
                client.disconnect()
            except Exception:
                pass

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
