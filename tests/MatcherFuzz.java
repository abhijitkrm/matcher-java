//! MatcherFuzz <corpus.cmd.jsonl> — replay a fuzzgen corpus, print the
//! canonical event stream (symbol-tagged for engine corpora) to stdout.
//! scripts/diffuzz.sh byte-diffs this output across implementations.

import io.github.abhijitkrm.matcher.Engine;
import io.github.abhijitkrm.matcher.JsonFlat;
import io.github.abhijitkrm.matcher.OrderBook;
import io.github.abhijitkrm.matcher.PriceIndex;
import io.github.abhijitkrm.matcher.Types.Command;
import io.github.abhijitkrm.matcher.Types.Event;
import io.github.abhijitkrm.matcher.Types.OType;
import io.github.abhijitkrm.matcher.Types.Side;
import io.github.abhijitkrm.matcher.Types.Tif;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MatcherFuzz {

    static Command parseCommand(String line) {
        switch (JsonFlat.get(line, "cmd")) {
            case "new": {
                Side side = "bid".equals(JsonFlat.get(line, "side")) ? Side.Bid : Side.Ask;
                OType otype = "market".equals(JsonFlat.get(line, "otype")) ? OType.Market : OType.Limit;
                Tif tif = switch (JsonFlat.get(line, "tif") == null ? "gtc" : JsonFlat.get(line, "tif")) {
                    case "ioc" -> Tif.Ioc;
                    case "fok" -> Tif.Fok;
                    case "post_only" -> Tif.PostOnly;
                    default -> Tif.Gtc;
                };
                Long price = JsonFlat.getLong(line, "price");
                return new Command.New(
                        JsonFlat.getLong(line, "order_id"), side, otype,
                        price == null ? 0 : price, JsonFlat.getLong(line, "qty"), tif);
            }
            case "cancel":
                return new Command.Cancel(JsonFlat.getLong(line, "order_id"));
            case "replace":
                return new Command.Replace(JsonFlat.getLong(line, "order_id"),
                        JsonFlat.getLong(line, "price"), JsonFlat.getLong(line, "qty"));
            default:
                throw new IllegalArgumentException("bad command line: " + line);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: MatcherFuzz <cmd.jsonl>");
            System.exit(2);
        }
        List<String> lines = Files.readAllLines(Path.of(args[0]));
        String hdr = lines.get(0);
        OrderBook.Config cfg = new OrderBook.Config(
                JsonFlat.getLong(hdr, "pmin"),
                JsonFlat.getLong(hdr, "pmax"),
                JsonFlat.getLong(hdr, "max_orders").intValue(),
                PriceIndex.Kind.Ladder);

        StringBuilder out = new StringBuilder(1 << 20);
        if ("true".equals(JsonFlat.get(hdr, "engine"))) {
            Engine eng = new Engine(cfg);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty()) continue;
                Long s = JsonFlat.getLong(line, "symbol");
                long sym = s == null ? 0 : s;
                eng.apply(sym, parseCommand(line), (seq, ev) ->
                        out.append(Event.canonical(seq, sym, ev)).append('\n'));
            }
        } else {
            OrderBook book = new OrderBook(cfg);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty()) continue;
                book.apply(parseCommand(line), (seq, ev) ->
                        out.append(Event.canonical(seq, ev)).append('\n'));
            }
        }
        System.out.print(out);
    }
}
