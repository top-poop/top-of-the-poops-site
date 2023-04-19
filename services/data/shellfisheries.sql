select name                                                           as name,
       st_npoints(wkb_geometry)                                           as points_original,
       st_npoints(st_simplifypreservetopology(wkb_geometry, 0.0001))   as points_reduced,
       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(wkb_geometry, 0.0001))) as geometry
from shellfish_view
order by name
