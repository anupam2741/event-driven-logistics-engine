import asyncio
import os
import redis
import httpx
import json
import logging
import math

# --- CONFIGURATION ---
REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
REDIS_CHANNEL = 'rider_missions'
JAVA_INGESTION_URL = os.environ.get('TRACKING_URL', 'http://localhost:8080/api/tracking/ping')
API_KEY = os.environ.get('API_KEY', '')

PING_INTERVAL = 2
MOVEMENT_STEP = 0.003  # ~333 m per step; at 2s/step the delivery leg takes ~1–2 min

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger(__name__)

# Global set to track cancelled rider IDs
cancelled_riders = set()

class RiderCancelledException(Exception):
    """Raised when a rider's order is cancelled mid-delivery."""
    pass

async def move_to_target(client, order_id, rider_id, start_lat, start_lng, target_lat, target_lng, label):
    curr_lat, curr_lng = start_lat, start_lng
    logger.info(f"[{rider_id}] STARTING JOURNEY TO: {label}")

    while True:
        # Check if this rider has been cancelled
        if rider_id in cancelled_riders:
            cancelled_riders.discard(rider_id)
            raise RiderCancelledException(f"Rider {rider_id} cancelled for order {order_id}")

        lat_diff = target_lat - curr_lat
        lng_diff = target_lng - curr_lng
        distance = math.sqrt(lat_diff**2 + lng_diff**2)

        current_status = None
        is_arrived = False

        if distance < 0.001:
            # Matches your OrderStatus Enum
            current_status = "PICKED_UP" if label == "PICKUP" else "DELIVERED"
            is_arrived = True

        step = MOVEMENT_STEP if distance > MOVEMENT_STEP else distance
        ratio = step / distance if distance > 0 else 1

        curr_lat += lat_diff * ratio
        curr_lng += lng_diff * ratio

        payload = {
            "orderId": order_id, # This is now the clean UUID string
            "riderId": rider_id,
            "status": current_status,
            "coordinates": {
                "lat": round(curr_lat, 14),
                "lng": round(curr_lng, 14)
            }
        }

        try:
            response = await client.post(JAVA_INGESTION_URL, json=payload, timeout=5.0,
                                         headers={"X-API-Key": API_KEY})
            if response.status_code in [200, 202]:
                status_log = f" | Status: {current_status}" if current_status else ""
                logger.info(f"[{rider_id}] PING SUCCESS -> {label} (Dist Rem: {distance:.4f})")
            else:
                logger.error(f"[{rider_id}] JAVA ERROR: {response.status_code} - {response.text}")
        except Exception as e:
            logger.error(f"[{rider_id}] CONNECTION FAILED: {type(e).__name__}")

        if is_arrived:
            return curr_lat, curr_lng

        await asyncio.sleep(PING_INTERVAL)

async def simulate_rider_lifecycle(rider_data):
    try:
        # --- THE FIX: SANITIZE THE ORDER ID ---
        order_id = str(rider_data['orderId'])


        rider_id = rider_data['riderId']
        c = rider_data['current']
        p = rider_data['pickup']
        d = rider_data['delivery']

        logger.info(f"[{rider_id}] MISSION START. Clean UUID: {order_id}")
    except KeyError as e:
        logger.error(f"DTO MISMATCH: Missing key {e}")
        return

    try:
        async with httpx.AsyncClient() as client:
            at_lat, at_lng = await move_to_target(client, order_id, rider_id, c['lat'], c['lng'], p['lat'], p['lng'], "PICKUP")

            logger.info(f"[{rider_id}] AT PICKUP. Waiting 5s...")
            await asyncio.sleep(5)

            await move_to_target(client, order_id, rider_id, at_lat, at_lng, d['lat'], d['lng'], "CUSTOMER")
            logger.info(f"[{rider_id}] DELIVERY COMPLETE.")
    except RiderCancelledException:
        logger.info(f"[{rider_id}] ORDER CANCELLED. Stopping movement.")

def _blocking_redis_listener(loop):
    try:
        r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        pubsub = r.pubsub()
        pubsub.subscribe(REDIS_CHANNEL)
        logger.info(f"LISTENING ON REDIS: {REDIS_HOST}:{REDIS_PORT} CHANNEL: {REDIS_CHANNEL}")

        for message in pubsub.listen():
            if message['type'] == 'message':
                try:
                    data = json.loads(message['data'])

                    # Handle cancellation messages
                    if data.get('type') == 'CANCEL':
                        rider_id = data.get('riderId')
                        order_id = data.get('orderId')
                        logger.info(f"[{rider_id}] CANCEL received for order {order_id}")
                        cancelled_riders.add(rider_id)
                        continue

                    asyncio.run_coroutine_threadsafe(simulate_rider_lifecycle(data), loop)
                except json.JSONDecodeError:
                    logger.error("MALFORMED JSON from Redis")
    except Exception as e:
        logger.critical(f"REDIS CONNECTION LOST: {e}")

async def redis_listener():
    loop = asyncio.get_running_loop()
    await loop.run_in_executor(None, _blocking_redis_listener, loop)

if __name__ == "__main__":
    try:
        asyncio.run(redis_listener())
    except KeyboardInterrupt:
        logger.info("Simulator stopped.")