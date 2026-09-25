//! matcherrun — e2e runner: apply an engine command stream, emit the canonical
//! tagged event journal to stdout, optionally write a snapshot at the end.
//!   java -cp out MatcherRun <engine.cmd.jsonl> [--snap <path>]

import io.github.abhijitkrm.matcher.Engine;
import io.github.abhijitkrm.matcher.JsonFlat;
import io.github.abhijitkrm.matcher.Journal;
import io.github.abhijitkrm.matcher.OrderBook;
import io.github.abhijitkrm.matcher.PriceIndex;
import io.github.abhijitkrm.matcher.Snapshot;
import io.github.abhijitkrm.matcher.Types.Command;
import io.github.abhijitkrm.matcher.Types.Event;
import io.github.abhijitkrm.matcher.Types.OType;
import io.github.abhijitkrm.matcher.Types.Side;
import io.github.abhijitkrm.matcher.Types.Tif;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MatcherRun {

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
            case "replace": {
                Long p = JsonFlat.getLong(line, "price");
                return new Command.Replace(JsonFlat.getLong(line, "order_id"),
                        p == null ? 0 : p, JsonFlat.getLong(line, "qty"));
            }
            default:
                return null;
        }
    }

    static OrderBook.Config parseHeader(String line) {
        Long mo = JsonFlat.getLong(line, "max_orders");
        return new OrderBook.Config(
                JsonFlat.getLong(line, "pmin"),
                JsonFlat.getLong(line, "pmax"),
                mo == null ? 65_536 : mo.intValue(),
                "tree".equals(JsonFlat.get(line, "index"))
                        ? PriceIndex.Kind.Tree : PriceIndex.Kind.Ladder);
    }

    public static void main(String[] args) throws Exception {
        String path = args[0];
        String snapPath = null;
        for (int i = 1; i + 1 < args.length; i += 2)
            if (args[i].equals("--snap")) snapPath = args[i + 1];

        List<String> lines = Files.readAllLines(Path.of(path));
        Engine eng = new Engine(parseHeader(lines.get(0)));
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;
            Command cmd = parseCommand(line);
            if (cmd == null) {
                System.err.println(path + ":" + (i + 1) + ": malformed command: " + line);
                System.exit(2);
            }
            Long s = JsonFlat.getLong(line, "symbol");
            long sym = s == null ? 0 : s;
            eng.applyTagged(sym, cmd, (s2, seq, ev) ->
                    Journal.journalEvent(s2, seq, ev, l -> out.append(l)));
        }
        System.out.print(out);
        if (snapPath != null)
            Files.writeString(Path.of(snapPath), Snapshot.writeEngine(eng));
    }
}
