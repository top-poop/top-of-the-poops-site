with settings as (select 0.0005 as tolerance)
select name1_text                                                           as name,
       st_npoints(geometry)                                           as points_original,
       st_npoints(st_simplifypreservetopology(geometry, tolerance))   as points_reduced,
       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(geometry, tolerance))) as geometry
from os_open_built_up_areas urb
    full join settings on 1=1
