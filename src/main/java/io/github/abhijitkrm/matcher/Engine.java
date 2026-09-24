//! Thin multi-symbol router: symbol → OrderBook. Sequencing stays per-book
//! (same contract as the single-symbol core).
package io.github.abhijitkrm.matcher;

import io.github.abhijitkrm.matcher.Types.Command;
import java.util.HashMap;
import java.util.Map;

public final class Engine {
    private final OrderBook.Config cfg;
    private final Map<Long, OrderBook> books = new HashMap<>();

    public Engine(OrderBook.Config cfg) { this.cfg = cfg; }

    /// Book for `symbol`, created on first use.
    public OrderBook book(long symbol) {
        return books.computeIfAbsent(symbol, k -> new OrderBook(cfg));
    }

    public void apply(long symbol, Command cmd, Sink sink) {
        book(symbol).apply(cmd, sink);
    }

    public int symbols() { return books.size(); }
}
