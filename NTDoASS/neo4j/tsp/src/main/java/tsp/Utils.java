package tsp;

import org.neo4j.graphdb.Relationship;

public class Utils {
    public static double getDistance(Relationship rel, String propertyName) {
        return (Double)rel.getProperty(propertyName);
    }
}
