import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

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