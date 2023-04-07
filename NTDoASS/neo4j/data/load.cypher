WITH "file:///" AS base
WITH base + "qa194_vertices.csv" AS uri
LOAD CSV WITH HEADERS FROM uri AS row
MERGE (city:City { id:row.id })
SET city.x = toFloat(row.x),
city.y = toFloat(row.y);

WITH "file:///" AS base
WITH base + "qa194_edges.csv" AS uri
LOAD CSV WITH HEADERS FROM uri AS row
MATCH (origin:City {id: row.id1})
MATCH (destination:City {id: row.id2})
MERGE (origin)-[:EROAD { distance: toFloat(row.distance), id1: row.id1, id2: row.id2 }]->(destination);

CALL tsp.solve("EROAD", "City", "distance", 100, 1) yield relationship, cumulativeDistance, log return relationship, cumulativeDistance, log;


MATCH (n:City)-[e:EROAD]->() DELETE e;
MATCH (n:City) DELETE n;