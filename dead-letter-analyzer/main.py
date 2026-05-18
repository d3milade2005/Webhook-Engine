import os, httpx, psycopg2, logging
import psycopg2.extras
from fastapi import FastAPI
from dotenv import load_dotenv
from apscheduler.schedulers.background import BackgroundScheduler
from datetime import datetime, timedelta

load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), ".env"))

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Dead Letter Analyzer")

def get_db():
    return psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "webhook_engine"),
        user=os.getenv("DB_USER"),
        password=os.getenv("DB_PASSWORD")
    )


def analyze_dead_letters():
    logger.info("Running dead letter analysis...")
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:

            # find endpoints with spike in failures in last 1 hour
            cur.execute("""
                SELECT
                    we.id AS endpoint_id,
                    we.url,
                    COUNT(*) AS failure_count
                FROM dead_letter_events dle
                JOIN webhook_endpoints we ON dle.endpoint_id = we.id
                WHERE dle.dead_lettered_at >= NOW() - INTERVAL '1 hour'
                GROUP BY we.id, we.url
                HAVING COUNT(*) >= 3
            """)
            spike_endpoints = cur.fetchall()

            for ep in spike_endpoints:
                logger.warning(
                    f"Failure spike detected: endpoint {ep['url']} "
                    f"has {ep['failure_count']} dead letters in the last hour"
                )
                send_alert(
                    f"Failure spike on endpoint {ep['url']} "
                    f"{ep['failure_count']} failed deliveries in the last hour"
                )

            # detect error patterns from delivery_attempts
            cur.execute("""
                SELECT
                    we.id AS endpoint_id,
                    we.url,
                    da.http_status,
                    COUNT(*) AS count
                FROM delivery_attempts da
                JOIN webhook_endpoints we ON da.endpoint_id = we.id
                WHERE da.attempted_at >= NOW() - INTERVAL '1 hour'
                  AND da.http_status IS NOT NULL
                GROUP BY we.id, we.url, da.http_status
                HAVING COUNT(*) >= 3
            """)
            patterns = cur.fetchall()

            for p in patterns:
                status = p['http_status']
                url = p['url']

                if status == 401:
                    msg = f"401 errors on {url}, auth token may need rotation"
                elif status == 404:
                    msg = f"404 errors on {url}, endpoint URL may have changed"
                elif status and status >= 500:
                    msg = f"{status} errors on {url}, downstream server issues"
                else:
                    continue

                logger.warning(msg)
                send_alert(msg)

            # auto-disable endpoints failing consistently for 24+ hours
            cur.execute("""
                SELECT
                    we.id AS endpoint_id,
                    we.url
                FROM dead_letter_events dle
                JOIN webhook_endpoints we ON dle.endpoint_id = we.id
                WHERE we.is_active = TRUE
                GROUP BY we.id, we.url
                HAVING MIN(dle.dead_lettered_at) <= NOW() - INTERVAL '24 hours'
                   AND COUNT(*) >= 5
            """)
            endpoints_to_disable = cur.fetchall()

            for ep in endpoints_to_disable:
                cur.execute("""
                    UPDATE webhook_endpoints
                    SET is_active = FALSE
                    WHERE id = %s
                """, (str(ep['endpoint_id']),))
                conn.commit()

                logger.error(
                    f"Auto-disabled endpoint {ep['url']}, "
                    f"consistently failing for 24+ hours"
                )
                send_alert(
                    f"Auto-disabled endpoint {ep['url']}, "
                    f"failing consistently for over 24 hours"
                )

    except Exception as e:
        logger.error(f"Analysis failed: {e}")
    finally:
        conn.close()


def send_alert(message: str):
    slack_url = os.getenv("SLACK_WEBHOOK_URL")
    if not slack_url:
        logger.info(f"[ALERT: no Slack configured] {message}")
        return

    try:
        httpx.post(slack_url, json={"text": message}, timeout=5)
    except Exception as e:
        logger.error(f"Failed to send Slack alert: {e}")


scheduler = BackgroundScheduler()
scheduler.add_job(analyze_dead_letters, "interval", minutes=15)
scheduler.start()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/analysis/run")
def run_analysis():
    """Manually trigger analysis"""
    analyze_dead_letters()
    return {"status": "analysis complete"}


@app.get("/dead-letters")
def get_dead_letters():
    """List all dead letter events"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT
                    dle.id,
                    dle.reason,
                    dle.dead_lettered_at,
                    e.event_type,
                    e.payload,
                    we.url AS endpoint_url
                FROM dead_letter_events dle
                JOIN events e ON dle.event_id = e.id
                JOIN webhook_endpoints we ON dle.endpoint_id = we.id
                ORDER BY dle.dead_lettered_at DESC
                LIMIT 100
            """)
            return cur.fetchall()
    finally:
        conn.close()


@app.get("/dead-letters/stats")
def get_stats():
    """Failure stats per endpoint"""
    conn = get_db()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT
                    we.url,
                    COUNT(*) AS total_failures,
                    MAX(dle.dead_lettered_at) AS last_failure,
                    we.is_active
                FROM dead_letter_events dle
                JOIN webhook_endpoints we ON dle.endpoint_id = we.id
                GROUP BY we.url, we.is_active
                ORDER BY total_failures DESC
            """)
            return cur.fetchall()
    finally:
        conn.close()