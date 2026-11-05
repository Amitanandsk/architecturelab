import http from 'k6/http';
import { Counter } from 'k6/metrics';

const processingTimeouts =
    new Counter('order_processing_timeouts');

const persistenceSaturated =
    new Counter('order_persistence_saturated');

const successfulOrders =
    new Counter('successful_orders');

const otherResponses =
    new Counter('other_responses');

export const options = {
    scenarios: {
        db_pool_saturation: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            maxDuration: '15s',
        },
    },
};

export default function () {

    const response = http.post(
        'http://localhost:8080/orders',
        JSON.stringify({
            sku: 'PROD-1',
            quantity: 2,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    if (response.status === 200) {
        successfulOrders.add(1);
        return;
    }

    try {
        const body = response.json();

        if (body.errorCode === 'ORDER_PROCESSING_TIMEOUT') {
            processingTimeouts.add(1);
        } else if (
            body.errorCode === 'ORDER_PERSISTENCE_SATURATED'
        ) {
            persistenceSaturated.add(1);
        } else {
            otherResponses.add(1);
        }
    } catch (_) {
        otherResponses.add(1);
    }
}