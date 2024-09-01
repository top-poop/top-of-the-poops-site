drop materialized view if exists pcon_simplified cascade;

create materialized view pcon_simplified as
SELECT pcon_july_2024_uk_bfc.pcon24nm,
       pcon_july_2024_uk_bfc.pcon24cd ,
       st_npoints(pcon_july_2024_uk_bfc.wkb_geometry) AS points_original,
       st_npoints(
               st_simplifypreservetopology(
                       pcon_july_2024_uk_bfc.wkb_geometry,
                       0.0005::double precision)
       )                                             AS points_reduced,
       st_simplifypreservetopology(
               pcon_july_2024_uk_bfc.wkb_geometry,
               0.0005::double precision
       )                                             AS wkb_geometry
FROM pcon_july_2024_uk_bfc;

create index pcon_simplified_wkb_geometry_geom_idx on pcon_simplified using gist (wkb_geometry);
