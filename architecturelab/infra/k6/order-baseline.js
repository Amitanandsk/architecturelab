import http from 'k6/http';
import { check } from 'k6';

export const options = {

    // Fixed concurrency:
    // 10 virtual users continuously execute requests for 30 seconds.
    vus: 10,
    duration: '30s',

    // Make p99 visible in the final console summary.
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
        'count'
    ],

    // For the first baseline, validate correctness.
    // We are NOT setting latency targets yet because
    // first we want to measure the current system.
    thresholds: {
        http_req_failed: ['rate==0'],
        checks: ['rate==1'],
    },
};

const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

export default function () {

    const payload = JSON.stringify({
        sku: 'PROD-1',
        quantity: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },

        // Gives this API operation a stable metric name.
        tags: {
            name: 'POST /orders',
        },
    };

    const response = http.post(
        `${BASE_URL}/orders`,
        payload,
        params
    );

    check(response, {
        'order creation returned 200': (response) =>
            response.status === 200,
    });
}