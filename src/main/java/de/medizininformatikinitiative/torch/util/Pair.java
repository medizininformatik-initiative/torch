package de.medizininformatikinitiative.torch.util;

public class Pair<X, Y> {
    public final X first;
    public final Y second;

    public Pair(X first, Y second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    // You can add methods like equals(), hashCode() if needed
}
