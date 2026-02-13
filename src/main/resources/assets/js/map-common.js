

export const COMPANY_COLORS = {
    'Anglian Water': '#00adef',      // Bright Sky Blue
    'Northumbrian Water': '#1d4ed8', // Deep Royal Blue
    'Severn Trent Water': '#059669', // Emerald Green
    'South West Water': '#f59e0b',   // Amber/Orange
    'Southern Water': '#7c3aed',     // Violet
    'Thames Water': '#2563eb',       // Classic Blue
    'United Utilities': '#db2777',   // Pink/Magenta
    'Wessex Water': '#0891b2',       // Teal
    'Dwr Cymru Welsh Water': '#5227af',
    'Default': '#64748b'             // Slate Gray
};

export const STYLE_URI = 'https://top-of-the-poops.org/tiles/styles/v4/style.json';


export function bbox(geojson) {

    const features = geojson.features;

    if ( features.length === 0) {
        return undefined
    }

    return features
        .filter(f => f.geometry?.type === "Point")          // only points
        .map(f => f.geometry.coordinates)                  // [[lng, lat], ...]
        .reduce((bbox, [lng, lat]) => [
            Math.min(bbox[0], lng),
            Math.min(bbox[1], lat),
            Math.max(bbox[2], lng),
            Math.max(bbox[3], lat)
        ], [Infinity, Infinity, -Infinity, -Infinity]);
}
