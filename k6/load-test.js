import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
/*
 * NOTE ON VIRTUAL USERS (VUs):
 *
 * The settings below are tuned for a live demo against an AWS EC2 t3.micro (Free Tier) instance.
 *
 * For local benchmarking (which generated the 4,800 RPS and 1M+ requests over 3.5m metrics),
 * the stages were configured for high concurrency:
 *   { duration: '30s', target: 50 },
 *   { duration: '1m', target: 500 },
 *   { duration: '1m', target: 1000 },
 *   { duration: '30s', target: 1000 },
 *   { duration: '30s', target: 0 }
 */
const successRate = new Rate('success_rate_200');
const rateLimitedRate = new Rate('rate_limited_429');

const TOKEN = __ENV.TOKEN;

export const options = {
    // Gentle load for a t3.micro free-tier server
    stages: [
        { duration: '10s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '10s', target: 0 },
    ]
};

export default function () {
    if (!TOKEN) {
        console.error("Please pass the TOKEN environment variable!");
        return;
    }

    // Notice we are hitting your AWS Public IP now!
    const res = http.get('http://52.63.125.173:8080/test/anything', {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
        },
    });

    successRate.add(res.status === 200);
    rateLimitedRate.add(res.status === 429);

    check(res, {
        'is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.1);
}