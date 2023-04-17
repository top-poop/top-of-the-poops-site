select site_name                                                           as name,
       st_npoints(geometry)                                           as points_original,
       st_npoints(st_simplifypreservetopology(geometry, 0.0001))   as points_reduced,
       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(geometry, 0.0001))) as geometry
from bathing_locations_view

