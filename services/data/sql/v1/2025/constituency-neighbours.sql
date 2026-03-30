select pcon24nm, neighbour
from pcon_neighbours
where neighbour_code like 'E%' or neighbour_code like 'W%' or neighbour_code like 'S%'