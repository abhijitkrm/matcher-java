//! matcherbench — spec/BENCH.md measurement protocol.
//!   java -cp out MatcherBench <corpus-prefix> [--tag name]

import io.github.abhijitkrm.matcher.JsonFlat;
import io.github.abhijitkrm.matcher.OrderBook;
import io.github.abhijitkrm.matcher.PriceIndex;
import io.github.abhijitkrm.matcher.Sink;
import io.github.abhijitkrm.matcher.Types.Command;
import io.github.abhijitkrm.matcher.Types.OType;
import io.github.abhijitkrm.matcher.Types.Side;
import io.github.abhijitkrm.matcher.Types.Tif;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class MatcherBench {

    static Command parseCommand(String line) {
        String cmd = JsonFlat.get(line, "cmd");
        if ("new".equals(cmd)) {
            Tif tif = switch (JsonFlat.get(line, "tif") == null ? "gtc" : JsonFlat.get(line, "tif")) {
                case "ioc" -> Tif.Ioc;
                case "fok" -> Tif.Fok;
                case "post_only" -> Tif.PostOnly;
                default -> Tif.Gtc;
            };
            return new Command.New(
                    JsonFlat.getLong(line, "order_id"),
                    "bid".equals(JsonFlat.get(line, "side")) ? Side.Bid : Side.Ask,
                    "market".equals(JsonFlat.get(line, "otype")) ? OType.Market : OType.Limit,
                    JsonFlat.getLong(line, "price") == null ? 0 : JsonFlat.getLong(line, "price"),
                    JsonFlat.getLong(line, "qty"), tif);
        }
        if ("cancel".equals(cmd)) return new Command.Cancel(JsonFlat.getLong(line, "order_id"));
        return new Command.Replace(
                JsonFlat.getLong(line, "order_id"),
                JsonFlat.getLong(line, "price"),
                JsonFlat.getLong(line, "qty"));
    }

    static Command[] load(String path, OrderBook.Config[] cfgOut) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(path));
        String h = lines.get(0);
        cfgOut[0] = new OrderBook.Config(
                JsonFlat.getLong(h, "pmin"), JsonFlat.getLong(h, "pmax"),
                JsonFlat.getLong(h, "max_orders").intValue(),
                "tree".equals(JsonFlat.get(h, "index")) ? PriceIndex.Kind.Tree : PriceIndex.Kind.Ladder);
        Command[] cmds = new Command[lines.size() - 1];
        for (int i = 1; i < lines.size(); i++) cmds[i - 1] = parseCommand(lines.get(i));
        return cmds;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: MatcherBench <corpus-prefix> [--tag name]");
            System.exit(2);
        }
        String prefix = args[0], tag = "matcher-java";
        for (int i = 1; i + 1 < args.length; i++)
            if (args[i].equals("--tag")) tag = args[i + 1];

        OrderBook.Config[] cfgBox = new OrderBook.Config[1];
        Command[] setup = load(prefix + ".setup.cmd.jsonl", cfgBox);
        OrderBook.Config cfg = cfgBox[0];
        Command[] run = load(prefix + ".run.cmd.jsonl", cfgBox);

        // Warmup: throwaway book, setup + first 10% of run (also JITs the path).
        {
            OrderBook book = new OrderBook(cfg);
            Sink.Null sink = new Sink.Null();
            for (Command c : setup) book.apply(c, sink);
            for (int i = 0; i < run.length / 10; i++) book.apply(run[i], sink);
        }

        OrderBook book = new OrderBook(cfg);
        Sink.Null sink = new Sink.Null();
        for (Command c : setup) book.apply(c, sink);

        long[] lat = new long[run.length];
        long t0 = System.nanoTime();
        for (int i = 0; i < run.length; i++) {
            long a = System.nanoTime();
            book.apply(run[i], sink);
            lat[i] = System.nanoTime() - a;
        }
        long totalNs = System.nanoTime() - t0;

        Arrays.sort(lat);
        double ops = run.length * 1e9 / totalNs;
        System.out.printf("%s: %d ops in %.1fms => %,.0f ops/s p50=%dns p90=%dns p99=%dns p99.9=%dns max=%dns checksum=%d%n",
                tag, run.length, totalNs / 1e6, ops,
                lat[(int) (lat.length * 0.50)], lat[(int) (lat.length * 0.90)],
                lat[(int) (lat.length * 0.99)], lat[(int) (lat.length * 0.999)],
                lat[lat.length - 1], sink.acc);
    }
}
