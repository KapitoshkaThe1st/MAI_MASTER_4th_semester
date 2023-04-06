WITH "file:///" AS base
WITH base + "simple_vertices.csv" AS uri
LOAD CSV WITH HEADERS FROM uri AS row
MERGE (city:City { id:row.id })
SET city.x = toFloat(row.x),
city.y = toFloat(row.y);

WITH "file:///" AS base
WITH base + "simple_edges.csv" AS uri
LOAD CSV WITH HEADERS FROM uri AS row
MATCH (origin:City {id: row.id1})
MATCH (destination:City {id: row.id2})
MERGE (origin)-[:EROAD { distance: toFloat(row.distance), id1: row.id1, id2: row.id2 }]->(destination);

WITH 1 AS origin_id

match (a:City) with collect(a) as nodeList call tsp.allSingleNodes(nodeList) yield singleNode return singleNode;


CALL tsp.solve('Test')
YIELD nodeId, componentId
RETURN



MATCH (n:City)-[e:EROAD]->() DELETE e;
MATCH (n:City) DELETE n;


MATCH ()-[r:EROAD]->() DELETE r;

CALL tsp.solve("1", "EROAD", "City", "distance") yield relationship return relationship;