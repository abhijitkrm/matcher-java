// The ladder's cursor agrees with a sorted reference under random churn on a
// wide ladder, including whole summary words emptying and refilling.
package io.github.abhijitkrm.matcher;

import io.github.abhijitkrm.matcher.Types.Side;
import java.util.TreeSet;

public final class LadderTest {
    public static void main(String[] args) {
        final long span = 1L << 20;
        for (Side side : new Side[] {Side.Bid, Side.Ask}) {
            PriceIndex lad = PriceIndex.ladder(side, 0, span - 1);
            TreeSet<Long> set = new TreeSet<>();
            long x = 0x9E3779B97F4A7C15L;
            for (int step = 0; step < 200_000; step++) {
                x ^= x << 13;
                x ^= x >>> 7;
                x ^= x << 17;
                long p = switch ((int) Long.remainderUnsigned(x, 8)) {
                    case 0 -> 0;
                    case 1 -> span - 1;
                    case 2 -> Long.remainderUnsigned(x >>> 8, span);
                    default -> span / 2 - 6000 + Long.remainderUnsigned(x >>> 8, 12_000);
                };
                if (step % 1000 < 520 && !set.isEmpty()) {
                    long q;
                    if ((x & 16) == 0) q = side == Side.Bid ? set.last() : set.first();
                    else {
                        Long c = set.ceiling(p);
                        q = c == null ? set.last() : c;
                    }
                    set.remove(q);
                    lad.levelMut(q).head = PriceIndex.NIL;
                    lad.unlinkLevel(q);
                } else if (set.add(p)) {
                    lad.levelInsert(p).head = 0;
                }
                long want = set.isEmpty() ? Long.MIN_VALUE : side == Side.Bid ? set.last() : set.first();
                if (lad.bestPrice() != want || lad.len() != set.size())
                    throw new AssertionError(side + " step " + step + ": best " + lad.bestPrice() + " want " + want);
            }
        }
        System.out.println("ladder: ok");
    }
}
