@echo off

set NEO4J_DATA=%cd%\neo4j_docker\

docker run -it --rm --publish=7474:7474 --publish=7687:7687 --volume=%NEO4J_DATA%\data:/data --volume=%NEO4J_DATA%\conf:/conf --volume=%NEO4J_DATA%\plugins:/plugins --volume=%NEO4J_DATA%\import:/import neo4j