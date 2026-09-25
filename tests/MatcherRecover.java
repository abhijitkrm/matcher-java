//! matcherrecover — e2e recovery: load a matcher-snap/1 snapshot, replay a
//! command journal tail, emit the canonical tagged event journal to stdout.
//! Exits nonzero on a malformed journal line (truncated tail write).
//!   java -cp out MatcherRecover <snap.jsonl> <cmd-tail.jsonl>

import io.github.abhijitkrm.matcher.Engine;
import io.github.abhijitkrm.matcher.JsonFlat;
import io.github.abhijitkrm.matcher.Snapshot;
import io.github.abhijitkrm.matcher.Types.Command;
import io.github.abhijitkrm.matcher.Types.Event;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MatcherRecover {
    public static void main(String[] args) throws Exception {
        String snap = Files.readString(Path.of(args[0]));
        Engine eng = Snapshot.restoreEngine(Snapshot.parse(snap));

        StringBuilder out = new StringBuilder();
        List<String> lines = Files.readAllLines(Path.of(args[1]));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.contains("\"format\"")) continue;
            Command cmd = MatcherRun.parseCommand(line);
            if (cmd == null) {
                System.err.println(args[1] + ":" + (i + 1) + ": malformed journal line: " + line);
                System.exit(2);
            }
            Long s = JsonFlat.getLong(line, "symbol");
            long sym = s == null ? 0 : s;
            eng.applyTagged(sym, cmd, (s2, seq, ev) ->
                    out.append(Event.canonical(seq, s2, ev)).append('\n'));
        }
        System.out.print(out);
    }
}
