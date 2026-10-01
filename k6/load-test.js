import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics to track the ratio of allowed vs blocked requests
const successRate = new Rate('success_rate_200');
const rateLimitedRate = new Rate('rate_limited_429');

// We'll pass this in via the command line
const TOKEN = __ENV.TOKEN;

export const options = {
    // A staged ramp-up as requested: 50 -> 500 -> 1000 VUs
    stages: [
        { duration: '30s', target: 50 },   // Warm up with 50 users
        { duration: '1m',  target: 500 },  // Ramp up to 500 users
        { duration: '1m',  target: 1000 }, // Stress test at 1000 users
        { duration: '30s', target: 1000 }, // Hold the stress
        { duration: '30s', target: 0 },    // Cool down
    ],
    thresholds: {
        // We want 95% of requests to complete in under 500ms
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    if (!TOKEN) {
        console.error("Please pass the TOKEN environment variable: k6 run -e TOKEN=<your_token> load-test.js");
        return;
    }

    const res = http.get('http://localhost:8080/test/anything', {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
        },
    });

    // Track the status codes
    successRate.add(res.status === 200);
    rateLimitedRate.add(res.status === 429);

    // Ensure the gateway isn't crashing (returning 5xx)
    check(res, {
        'is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    // 100ms pause between requests per virtual user
    sleep(0.1);
}