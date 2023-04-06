package tsp;

import org.neo4j.graphdb.*;
import org.neo4j.procedure.*;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Tsp {
    @Context
    public Transaction tx;

    private String nodeToString(Node node) {
        var id = (String) node.getProperty("id");
        var x = (Double) node.getProperty("x");
        var y = (Double) node.getProperty("y");

        return String.format("Node { id: %s, x: %f, y: %f}", id, x, y);
    }

    private String edgeToString(Relationship relationship) {

        var distance = (Double) relationship.getProperty("distance");
        return String.format(String.format("Relationship { id1: %s, id2: %s, destination: %f}}",
                nodeToString(relationship.getStartNode()),
                nodeToString(relationship.getEndNode()),
                distance));
    }

    @Procedure
    @Description("tsp.allSingleNodes(nodes) yield singleNode")
    public Stream<TspResult> solve(
            @Name("originId") String originId,
            @Name("relationshipTypeName") String relationshipTypeName,
            @Name("nodeTypeName") String nodeTypeName,
            @Name("relationshipPropertyName") String relationshipPropertyName)
    {

        var sb = new StringBuilder();

        final Label NODE = Label.label(nodeTypeName);

        sb.append(String.format("NODE: %s%n", NODE));

        final RelationshipType relationshipType = StreamSupport
                .stream(tx.getAllRelationshipTypes().spliterator(), false)
                .filter(t -> relationshipTypeName.equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Relationship type not found"));

        sb.append(String.format("relationshipType: %s%n", relationshipType));

        var nodesIterator = tx.findNodes(NODE);

//        var originOpt = nodesIterator
//                .stream()
//                .filter(n -> originId.equals(n.getElementId()))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("Origin node not found"));

        var originOpt = nodesIterator
                .stream()
                .filter(n -> {
                    var id = (String)n.getProperty("id");
                    return originId.equals(id);
                })
                .findFirst();

        if(originOpt.isEmpty()) {

            nodesIterator = tx.findNodes(NODE);
            nodesIterator
                    .stream().forEach(n -> sb.append(String.format("NODE: %s%n", n.getElementId())));

            sb.append("Origin node not found%n");

            return Stream.of(new TspResult(sb.toString()));
        }

        Node origin = originOpt.get();

        var nextVertex = origin;

        var usedVertices = new HashSet<Node>();
        usedVertices.add(origin);

        var order = new LinkedHashSet<Relationship>();

        boolean edgesAreAvailable = false;

        int iteration = 0;
        while(true) {
            sb.append(String.format("iteration: %d%n", iteration));

            var availableEdges = nextVertex
                    .getRelationships(Direction.OUTGOING, relationshipType)
                    .stream()
                    .filter(e -> !order.contains(e) && !usedVertices.contains(e.getEndNode()))
                    .toList();

            edgesAreAvailable = !availableEdges.isEmpty();

            sb.append(String.format("edgesAreAvailable: %b%n", edgesAreAvailable));

            if(!edgesAreAvailable){
                break;
            }

//            var smallestEdge = availableEdges.stream().collect(Collectors.minBy((e1, e2) -> {
//                var weight1 = (Double)e1.getProperty(relationshipPropertyName);
//                var weight2 = (Double)e2.getProperty(relationshipPropertyName);
//
//                return Double.compare(weight1, weight2);
//            })).get();

            var smallestEdge = availableEdges.stream().min((e1, e2) -> {
                var weight1 = (Double) e1.getProperty(relationshipPropertyName);
                var weight2 = (Double) e2.getProperty(relationshipPropertyName);

                return Double.compare(weight1, weight2);
            }).get();

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

//        return order.stream().map(TspResult::new);
        return Stream.concat(order.stream().map(TspResult::new), Stream.of(new TspResult(sb.toString())));
    }

    public static class TspResult {
        public String log;
        public Relationship relationship;

        public TspResult(Relationship relationship) {
            this.relationship = relationship;
        }
        public TspResult(String log) {
            this.log = log;
        }
    }
}
