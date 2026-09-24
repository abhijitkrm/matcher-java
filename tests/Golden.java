//! Golden vector runner: every vectors/**\/*.cmd.jsonl through OrderBook,
//! canonical event serialization diffed against *.evt.jsonl.
//!   java -cp out Golden <vectors-dir>

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class Golden {

    static Command parseCommand(String line) {
        String cmd = JsonFlat.get(line, "cmd");
        switch (cmd) {
            case "new": {
                Side side = "bid".equals(JsonFlat.get(line, "side")) ? Side.Bid : Side.Ask;
                OType otype = "market".equals(JsonFlat.get(line, "otype")) ? OType.Market : OType.Limit;
                Tif tif = switch (JsonFlat.get(line, "tif") == null ? "gtc" : JsonFlat.get(line, "tif")) {
                    case "ioc" -> Tif.Ioc;
                    case "fok" -> Tif.Fok;
                    case "post_only" -> Tif.PostOnly;
                    default -> Tif.Gtc;
                };
                return new Command.New(
                        JsonFlat.getLong(line, "order_id"), side, otype,
                        JsonFlat.getLong(line, "price") == null ? 0 : JsonFlat.getLong(line, "price"),
                        JsonFlat.getLong(line, "qty"), tif);
            }
            case "cancel":
                return new Command.Cancel(JsonFlat.getLong(line, "order_id"));
            case "replace":
                return new Command.Replace(
                        JsonFlat.getLong(line, "order_id"),
                        JsonFlat.getLong(line, "price"),
                        JsonFlat.getLong(line, "qty"));
            default:
                throw new IllegalArgumentException("unknown cmd: " + line);
        }
    }

    static OrderBook.Config parseHeader(String line, PriceIndex.Kind mode) {
        return new OrderBook.Config(
                JsonFlat.getLong(line, "pmin"),
                JsonFlat.getLong(line, "pmax"),
                JsonFlat.getLong(line, "max_orders").intValue(),
                mode);
    }

    static String runVector(Path cmdPath, PriceIndex.Kind mode) throws IOException {
        List<String> lines = Files.readAllLines(cmdPath);
        OrderBook book = new OrderBook(parseHeader(lines.get(0), mode));
        Sink.Vec sink = new Sink.Vec();
        for (int i = 1; i < lines.size(); i++) {
            if (!lines.get(i).isEmpty()) book.apply(parseCommand(lines.get(i)), sink);
        }
        return sink.canonical();
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "vectors");
        List<Path> cmdFiles;
        try (Stream<Path> s = Files.walk(dir)) {
            cmdFiles = s.filter(p -> p.toString().endsWith(".cmd.jsonl")).sorted().toList();
        }
        if (cmdFiles.isEmpty()) {
            System.err.println("no vectors found under " + dir);
            System.exit(1);
        }

        int checked = 0, failed = 0;
        for (Path cmdPath : cmdFiles) {
            Path evtPath = Path.of(cmdPath.toString().replace(".cmd.jsonl", ".evt.jsonl"));
            List<String> evtLines = Files.readAllLines(evtPath);
            String expected = String.join("\n", evtLines.subList(1, evtLines.size())) + "\n";

            String headerLine = Files.readAllLines(cmdPath).get(0);
            String mode = JsonFlat.get(headerLine, "index");
            List<PriceIndex.Kind> modes = switch (mode == null ? "ladder" : mode) {
                case "tree" -> List.of(PriceIndex.Kind.Tree);
                case "both" -> List.of(PriceIndex.Kind.Ladder, PriceIndex.Kind.Tree);
                default -> List.of(PriceIndex.Kind.Ladder);
            };

            for (PriceIndex.Kind m : modes) {
                String actual;
                try {
                    actual = runVector(cmdPath, m);
                } catch (Exception e) {
                    System.out.println("FAIL " + cmdPath.getFileName() + " [" + m + "]: threw " + e);
                    failed++;
                    continue;
                }
                if (!actual.equals(expected)) {
                    System.out.println("FAIL " + cmdPath.getFileName() + " [" + m + "]: golden mismatch");
                    String[] el = expected.split("\n"), al = actual.split("\n");
                    for (int i = 0; i < Math.max(el.length, al.length); i++) {
                        String e = i < el.length ? el[i] : "<none>";
                        String a = i < al.length ? al[i] : "<none>";
                        if (!e.equals(a)) {
                            System.out.println("  line " + (i + 2) + ":\n    expected " + e + "\n    actual   " + a);
                        }
                    }
                    failed++;
                } else {
                    checked++;
                }
            }
        }

        if (failed > 0) {
            System.out.println("golden: " + checked + " ok, " + failed + " FAILED");
            System.exit(1);
        }
        System.out.println("golden: " + checked + " vectors passed");
    }
}
