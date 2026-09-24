# Changelog

## v0.1.0 — 2025-01-XX

Initial release.

- `OrderBook` + `Engine`, `Sink` event seam
- Limit/Market, New/Cancel/Replace, GTC/IOC/FOK/Post-Only
- FIFO price-time priority, maker-price execution
- Pooled orders in flat arrays, intrusive FIFO levels, bitmap ladder index,
  `TreeMap` fallback for unbounded prices
- 41 golden vectors passing — byte-identical event streams
- ~11M orders/sec on shared benchmark workloads
- Zero dependencies, Java 17+
