//! Snapshot/journal round-trip checks (spec/JOURNAL.md):
//!   snap → restore → snap  = byte-identical
//!   restore → continue     = byte-identical event stream vs uninterrupted run
//!   cmd journal → replay   = identical event journal
//!   java -cp out SnapshotTest <vectors-dir>

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotTest {

    record Tagged(long sym, Command cmd) {}

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
                return new Command.New(JsonFlat.getLong(line, "order_id"), side, ot,
                        JsonFlat.getLong(line, "price") == null ? 0 : JsonFlat.getLong(line, "price"),
                        JsonFlat.getLong(line, "qty"), tif);
            }
            case "cancel":
                return new Command.Cancel(JsonFlat.getLong(line, "order_id"));
            default:
                Long p = JsonFlat.getLong(line, "price");
                return new Command.Replace(JsonFlat.getLong(line, "order_id"),
                        p == null ? 0 : p, JsonFlat.getLong(line, "qty"));
        }
    }

    static List<Tagged> loadEngineCmds(String dir) throws IOException {
        List<Tagged> cmds = new ArrayList<>();
        for (String line : Files.readAllLines(
                Path.of(dir, "engine", "001_multisymbol.cmd.jsonl"))) {
            if (line.isEmpty() || line.contains("\"format\"")) continue;
            Long s = JsonFlat.getLong(line, "symbol");
            cmds.add(new Tagged(s == null ? 0 : s, parseCommand(line)));
        }
        return cmds;
    }

    static String runTagged(Engine e, List<Tagged> cmds, int lo, int hi) {
        StringBuilder out = new StringBuilder();
        for (int i = lo; i < hi; i++) {
            Tagged t = cmds.get(i);
            e.applyTagged(t.sym(), t.cmd(), (s, seq, ev) ->
                    out.append(Event.canonical(seq, s, ev)).append('\n'));
        }
        return out.toString();
    }

    static int fails = 0;
    static void check(boolean ok, String name) { check(ok, name, ""); }
    static void check(boolean ok, String name, String detail) {
        if (!ok) {
            fails++;
            System.out.println("FAIL " + name + "\n" + detail);
        } else {
            System.out.println("ok   " + name);
        }
    }

    public static void main(String[] args) throws IOException {
        String dir = args.length > 0 ? args[0] : "vectors";
        List<Tagged> cmds = loadEngineCmds(dir);
        OrderBook.Config cfg = new OrderBook.Config(0, 1_000_000, 65_536, PriceIndex.Kind.Ladder);
        int split = cmds.size() / 2;

        // 1. Reference: uninterrupted run.
        String expected = runTagged(new Engine(cfg), cmds, 0, cmds.size());

        // 2. Split run: snapshot at midpoint, restore, continue.
        Engine eng = new Engine(cfg);
        String out = runTagged(eng, cmds, 0, split);
        String snap = Snapshot.writeEngine(eng);
        Engine eng2 = Snapshot.restoreEngine(Snapshot.parse(snap));

        String snap2 = Snapshot.writeEngine(eng2);
        check(snap.equals(snap2), "re-snapshot byte-identical",
                "left:\n" + snap + "\nright:\n" + snap2);

        out += runTagged(eng2, cmds, split, cmds.size());
        check(out.equals(expected), "continuation byte-identical");

        // 3. Journal replay.
        StringBuilder cmdLog = new StringBuilder(), evtLog = new StringBuilder();
        Engine eng3 = new Engine(cfg);
        for (Tagged t : cmds) {
            new Journal.CmdJournal(s -> cmdLog.append(s), t.sym()).record(t.cmd());
            eng3.applyTagged(t.sym(), t.cmd(), (s, seq, ev) ->
                    Journal.journalEvent(s, seq, ev, l -> evtLog.append(l)));
        }
        Engine eng4 = new Engine(cfg);
        StringBuilder replayed = new StringBuilder();
        for (String line : cmdLog.toString().split("\n")) {
            if (line.isEmpty()) continue;
            Long s = JsonFlat.getLong(line, "symbol");
            long sym = s == null ? 0 : s;
            eng4.applyTagged(sym, parseCommand(line), (s2, seq, ev) ->
                    replayed.append(Event.canonical(seq, s2, ev)).append('\n'));
        }
        check(replayed.toString().equals(evtLog.toString()), "journal replay byte-identical");

        // 4. Empty engine snapshot round-trips.
        check(Snapshot.parse(Snapshot.writeEngine(new Engine(cfg))).books().isEmpty(),
                "empty snapshot has no books", "");

        // 5. Mid-fuzz stream: deterministic LCG.
        {
            OrderBook.Config fcfg = new OrderBook.Config(0, 1000, 4096, PriceIndex.Kind.Ladder);
            long[] lcg = {0xC0FFEE};
            java.util.function.IntUnaryOperator below = n -> {
                lcg[0] = (lcg[0] * 1664525 + 1013904223) & 0xFFFFFFFFL;
                return (int) (lcg[0] % n);
            };
            List<Tagged> fcmds = new ArrayList<>();
            for (int i = 0; i < 4000; i++) {
                long sym = below.applyAsInt(6);
                long id = below.applyAsInt(256);
                Command c;
                switch (below.applyAsInt(3)) {
                    case 0 -> {
                        Tif tif = switch (below.applyAsInt(4)) {
                            case 0 -> Tif.Ioc;
                            case 1 -> Tif.Fok;
                            case 2 -> Tif.PostOnly;
                            default -> Tif.Gtc;
                        };
                        c = Command.newLimit(id,
                                below.applyAsInt(2) == 0 ? Side.Bid : Side.Ask,
                                below.applyAsInt(999) + 1, below.applyAsInt(200) + 1, tif);
                    }
                    case 1 -> c = Command.cancel(id);
                    default -> c = Command.replace(id, below.applyAsInt(999) + 1,
                            below.applyAsInt(200) + 1);
                }
                fcmds.add(new Tagged(sym, c));
            }
            String fexp = runTagged(new Engine(fcfg), fcmds, 0, fcmds.size());
            Engine feng = new Engine(fcfg);
            String fout = runTagged(feng, fcmds, 0, 2000);
            Engine feng2 = Snapshot.restoreEngine(Snapshot.parse(Snapshot.writeEngine(feng)));
            fout += runTagged(feng2, fcmds, 2000, fcmds.size());
            check(fout.equals(fexp), "mid-fuzz restore byte-identical");
        }

        if (fails > 0) System.exit(1);
        System.out.println("snapshot: all checks passed");
    }
}
