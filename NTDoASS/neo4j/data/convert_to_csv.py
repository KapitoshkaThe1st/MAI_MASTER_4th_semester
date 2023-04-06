import sys
import math
import csv

def basename(file_path):
    file_name = file_path.split('\\')[-1]
    base_name = file_name.split('.')[0]
    return base_name

def euq2d(x1, y1, x2, y2):
    dx = x1 - x2
    dy = y1 - y2

    return math.sqrt(dx * dx + dy * dy)

tsp_file_path = sys.argv[1]

vertices = []

with open(tsp_file_path) as file:
    while True:
        line = file.readline()
        if line.startswith('NODE_COORD_SECTION'):
            break
    
    for line in file:
        if line.startswith('EOF'):
            break

        words = line.split(' ')

        id = words[0]
        x = float(words[1])
        y = float(words[2])

        vertices.append((id, x, y))



edges = []

for v1 in vertices:
    for v2 in vertices:
        if v1 is v2:
            continue

        dist = euq2d(v1[1], v1[2], v2[1], v2[2])
        edges.append((v1[0], v2[0], dist))


base_name = basename(tsp_file_path)
vertices_path = base_name + '_vertices.csv'
edges_path = base_name + '_edges.csv'

with open(vertices_path, 'w', newline='') as csvfile:
    csv_writer = csv.writer(csvfile, delimiter=',')
    csv_writer.writerow(['id', 'x', 'y'])
    csv_writer.writerows(vertices)

with open(edges_path, 'w', newline='') as csvfile:
    csv_writer = csv.writer(csvfile, delimiter=',')
    csv_writer.writerow(['id1', 'id2', 'distance'])
    csv_writer.writerows(edges)