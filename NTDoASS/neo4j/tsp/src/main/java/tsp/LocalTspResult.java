package tsp;

import org.neo4j.graphdb.Relationship;

import java.util.Collection;
import java.util.Collections;

public class LocalTspResult {
    private final Collection<Relationship> path;
    private final double totalDistance;

    public LocalTspResult(Collection<Relationship> path, String propertyName) {
        this.path = path;
        totalDistance = this.path
                .stream()
                .mapToDouble(r -> Utils.getDistance(r, propertyName))
                .sum();
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public Collection<Relationship> getPath() {
        return Collections.unmodifiableCollection(path);
    }
}
