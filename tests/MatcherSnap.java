//! snapdump — apply an engine command stream, print the snapshot to stdout.
//! Cross-language snapshot parity check (spec/JOURNAL.md).
//!   java -cp out MatcherSnap <engine.cmd.jsonl>

import io.github.abhijitkrm.matcher.Engine;
import io.github.abhijitkrm.matcher.JsonFlat;
import io.github.abhijitkrm.matcher.OrderBook;
import io.github.abhijitkrm.matcher.PriceIndex;
import io.github.abhijitkrm.matcher.Snapshot;
import io.github.abhijitkrm.matcher.Types.Command;
import io.github.abhijitkrm.matcher.Types.OType;
import io.github.abhijitkrm.matcher.Types.Side;
import io.github.abhijitkrm.matcher.Types.Tif;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MatcherSnap {

    static Command parseCommand(String line) {
        switch (JsonFlat.get(line, "cmd")) {
            case "new": {
                Side side = "ask".equals(JsonFlat.get(line, "side")) ? Side.Ask : Side.Bid;
                OType ot = "market".equals(JsonFlat.get(line, "otype")) ? OType.Market : OType.Limit;
                String t = JsonFlat.get(line, "tif");
                Tif tif = switch (t == null ? "gtc" : t) {
                    case "ioc" -> Tif.Ioc;
                    case "fok" -> Tif.Fok;
                    case "post_only" -> Tif.PostOnly;
                    default -> Tif.Gtc;
                };
                Long p = JsonFlat.getLong(line, "price");
                return new Command.New(JsonFlat.getLong(line, "order_id"), side, ot,
                        p == null ? 0 : p, JsonFlat.getLong(line, "qty"), tif);
            }
            case "cancel":
                return new Command.Cancel(JsonFlat.getLong(line, "order_id"));
            default:
                Long p = JsonFlat.getLong(line, "price");
                return new Command.Replace(JsonFlat.getLong(line, "order_id"),
                        p == null ? 0 : p, JsonFlat.getLong(line, "qty"));
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(args[0]));
        String h = lines.get(0);
        OrderBook.Config cfg = new OrderBook.Config(
                JsonFlat.getLong(h, "pmin"),
                JsonFlat.getLong(h, "pmax"),
                JsonFlat.getLong(h, "max_orders").intValue(),
                "tree".equals(JsonFlat.get(h, "index"))
                        ? PriceIndex.Kind.Tree : PriceIndex.Kind.Ladder);
        Engine eng = new Engine(cfg);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;
            Long s = JsonFlat.getLong(line, "symbol");
            long sym = s == null ? 0 : s;
            eng.applyTagged(sym, parseCommand(line), (s2, seq, ev) -> {});
        }
        System.out.print(Snapshot.writeEngine(eng));
    }
}
