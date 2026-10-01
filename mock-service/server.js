const express = require('express');
const app = express();

const PORT = process.env.PORT || 9000;

// Set baseline behavior via environment variables (defaults to perfectly healthy)
const DEFAULT_ERROR_RATE = parseFloat(process.env.ERROR_RATE || '0'); // 0.0 to 1.0
const DEFAULT_DELAY_MS = parseInt(process.env.DELAY_MS || '0', 10);

app.use((req, res) => {
    // Allow overriding behavior on the fly via query parameters
    // e.g., http://localhost:8080/test/anything?errorRate=0.8&delay=4000
    const errorRate = req.query.errorRate ? parseFloat(req.query.errorRate) : DEFAULT_ERROR_RATE;
    const delayMs = req.query.delay ? parseInt(req.query.delay, 10) : DEFAULT_DELAY_MS;

    setTimeout(() => {
        // Randomly simulate a 500 Internal Server Error based on the error rate
        if (Math.random() < errorRate) {
            return res.status(500).json({
                error: "Simulated downstream failure",
                timestamp: new Date().toISOString()
            });
        }

        // Otherwise return a healthy 200 OK
        res.status(200).json({
            message: "Mock service responded successfully",
            path: req.path,
            method: req.method,
            timestamp: new Date().toISOString()
        });
    }, delayMs);
});

app.listen(PORT, () => {
    console.log(`Mock service running on port ${PORT}`);
    console.log(`Default Error Rate: ${DEFAULT_ERROR_RATE}`);
    console.log(`Default Delay: ${DEFAULT_DELAY_MS}ms`);
});