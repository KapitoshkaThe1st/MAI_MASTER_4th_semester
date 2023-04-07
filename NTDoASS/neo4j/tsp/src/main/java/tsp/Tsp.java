package tsp;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.graphdb.*;
import org.neo4j.procedure.*;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Tsp {
    @Context
    public Transaction tx;

    private <T> Optional<T> selectRandom(List<T> items, ToDoubleFunction<T> key, Random rand, StringBuilder sb) {
        if (items.isEmpty()) {
            return Optional.empty();
        } else if (items.size() == 1) {
            return Optional.of(items.get(0));
        } else {
            var values = items.stream().mapToDouble(key);
            var prefixSum = values
                    .collect(ArrayList<Double>::new, (sums, number) -> {
                        if (sums.isEmpty()) {
                            sums.add(number);
                        } else {
                            sums.add(sums.get(sums.size() - 1) + number);
                        }
                    }, (sums1, sums2) -> {
                        if (!sums1.isEmpty()) {
                            double sum = sums1.get(sums1.size() - 1);
                            sums2.replaceAll(num -> sum + num);
                        }
                        sums1.addAll(sums2);
                    });

            var sum = prefixSum.get(prefixSum.size() - 1);

            var value = rand.nextDouble() * sum;

            sb.append(String.format("prefix sum:%n"));
            prefixSum.forEach(d -> sb.append(String.format("%f%n", d)));

            var index = prefixSum.size() - 1;
            for(; index >= 0; --index) {
                if(prefixSum.get(index) < value) {
                    break;
                }
            }
            index++;

            sb.append(String.format("index to select: %d%n", index));

            var result = items.get(index);

            return Optional.of(result);
        }
    }

    private String nodeToString(Node node) {
        var id = (String) node.getProperty("id");
        var x = (Double) node.getProperty("x");
        var y = (Double) node.getProperty("y");

        return String.format("Node { id: %s, x: %f, y: %f}", id, x, y);
    }

    private String edgeToString(Relationship relationship) {

        var distance = (Double) relationship.getProperty("distance");
        return String.format(String.format("Relationship { id1: %s, id2: %s, distance: %f}}",
                nodeToString(relationship.getStartNode()),
                nodeToString(relationship.getEndNode()),
                distance));
    }

    private List<Node> getOrigins(Label nodeLabel, long samplesCount, StringBuilder sb) {
        var nodesIterator = tx.findNodes(nodeLabel);

        var origins = nodesIterator
                .stream()
                .collect(Collectors.toList());

        Collections.shuffle(origins);

        return origins.stream().limit(samplesCount).toList();
    }

    private LocalTspResult iteration(Node origin, long topCount, String relationshipPropertyName, RelationshipType relationshipType, Random rand, StringBuilder sb) {
        var nextVertex = origin;

        var usedVertices = new HashSet<Node>();
        usedVertices.add(origin);

        var order = new LinkedHashSet<Relationship>();

        boolean edgesAreAvailable = false;

        int iteration = 0;
        while (true) {
            sb.append(String.format("iteration: %d%n", iteration));

            var availableEdges = nextVertex
                    .getRelationships(Direction.OUTGOING, relationshipType)
                    .stream()
                    .filter(e -> !order.contains(e) && !usedVertices.contains(e.getEndNode()))
                    .collect(Collectors.toList());

            edgesAreAvailable = !availableEdges.isEmpty();

            sb.append(String.format("edgesAreAvailable: %b%n", edgesAreAvailable));

            if (!edgesAreAvailable) {
                break;
            }

            availableEdges.sort(Comparator.comparingDouble(e -> Utils.getDistance(e, relationshipPropertyName)));

            var pretendents = IntStream
                    .range(0, availableEdges.size())
                    .mapToObj(i -> new ImmutablePair<>(i + 1, availableEdges.get(i)))
                    .limit(topCount)
                    .toList();

            var smallestEdge = selectRandom(pretendents, e -> e.left * e.left, rand, sb).get().right;

            sb.append(String.format("smallestEdge: %s%n", edgeToString(smallestEdge)));

            order.add(smallestEdge);

            nextVertex = smallestEdge.getEndNode();
            usedVertices.add(nextVertex);

            sb.append(String.format("nextVertex: %s%n", nodeToString(nextVertex)));

            iteration++;
        }

        nextVertex
                .getRelationships(Direction.OUTGOING, relationshipType)
                .stream().filter(r -> origin.equals(r.getEndNode())).findFirst().ifPresent(order::add);

        return new LocalTspResult(order, relationshipPropertyName);
    }

    @Procedure
    @Description("tsp.solve(nodes) yield relationship, cumulativeDistance, log")
    public Stream<TspResult> solve(
            @Name("relationshipTypeName") String relationshipTypeName,
            @Name("nodeTypeName") String nodeTypeName,
            @Name("relationshipPropertyName") String relationshipPropertyName,
            @Name("samplesCount") Long samplesCount,
            @Name("topCount") Long topCount) {

        var sb = new StringBuilder();

        try {
            final Label nodeLabel = Label.label(nodeTypeName);

            sb.append(String.format("nodeLabel: %s%n", nodeLabel));

            final RelationshipType relationshipType = StreamSupport
                    .stream(tx.getAllRelationshipTypes().spliterator(), false)
                    .filter(t -> relationshipTypeName.equals(t.name()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Relationship type not found"));

            sb.append(String.format("relationshipType: %s%n", relationshipType));

            var origins = getOrigins(nodeLabel, samplesCount, sb);

            var rand = new Random(0);

            LocalTspResult best = null;

            for(int i = 0; i < samplesCount; ++i){
                var origin = origins.get(i % origins.size());
                var result = iteration(origin, topCount, relationshipPropertyName, relationshipType, rand, sb);

                if(best == null || result.getTotalDistance() < best.getTotalDistance()) {
                    best = result;
                }
            }


            if(best == null) {
                return Stream.of(new TspResult(sb.toString()));
            }

            double cumulativeDistance = 0.0;
            var bestPath = best.getPath();
            var results = new ArrayList<TspResult>(bestPath.size());
            for(var road : best.getPath()) {
                cumulativeDistance += Utils.getDistance(road, relationshipPropertyName);
                results.add(new TspResult(road, cumulativeDistance));
            }
            return Stream.concat(results.stream(), Stream.of(new TspResult(sb.toString())));
        }
        catch (Throwable ex) {
            sb.append(ex.getMessage());
            return Stream.of(new TspResult(sb.toString()));
        }
    }

    public static class TspResult {
        public String log;
        public Relationship relationship;
        public Double cumulativeDistance;

        public TspResult(Relationship relationship, double cumulativeDistance) {
            this.relationship = relationship;
            this.cumulativeDistance = cumulativeDistance;
        }

        public TspResult(String log) {
            this.log = log;
        }
    }
}
